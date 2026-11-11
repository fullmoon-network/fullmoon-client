/* Server List Ping.

The server card used to show a motd, a player count and a latency that came
out of the catalogue — numbers that were the same whether the server was up,
down or fictional. This asks the server.

The protocol is the handshake half of the vanilla one and has been stable
since 1.7: connect, send handshake with next-state 1, send an empty status
request, read a JSON blob back. Then send a ping payload and time the pong —
that round trip is the latency the game itself would report, not a TCP
connect time. Everything is length-prefixed with varints, packets included,
so the reader has to know how long a varint is before it knows how much to
read. */
use std::time::{Duration, Instant};

use serde::{Deserialize, Serialize};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpStream,
    time::timeout,
};

use crate::error::{Error, Result};

const PROTOCOL_UNKNOWN: i32 = -1;
const DEFAULT_PORT: u16 = 25565;
const DEADLINE: Duration = Duration::from_secs(5);

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerStatus {
    pub online: bool,
    pub motd: String,
    pub players: u32,
    pub max_players: u32,
    pub ping_ms: u32,
    pub version: String,
    /// data: URI of the 64x64 server icon, when the server sends one
    pub favicon: Option<String>,
    pub error: Option<String>,
}

impl ServerStatus {
    fn offline(why: String) -> Self {
        Self {
            online: false,
            motd: String::new(),
            players: 0,
            max_players: 0,
            ping_ms: 0,
            version: String::new(),
            favicon: None,
            error: Some(why),
        }
    }
}

pub async fn status(address: &str) -> ServerStatus {
    match timeout(DEADLINE, query(address)).await {
        Ok(Ok(s)) => s,
        Ok(Err(e)) => ServerStatus::offline(e.to_string()),
        Err(_) => ServerStatus::offline("timed out".into()),
    }
}

async fn query(address: &str) -> Result<ServerStatus> {
    let (host, port) = split_address(address);
    let mut sock = TcpStream::connect((host.as_str(), port)).await?;
    sock.set_nodelay(true)?;

    let mut handshake = Vec::new();
    write_varint(&mut handshake, 0x00);
    write_varint(&mut handshake, PROTOCOL_UNKNOWN);
    write_string(&mut handshake, &host);
    handshake.extend_from_slice(&port.to_be_bytes());
    write_varint(&mut handshake, 1); // next state: status
    write_packet(&mut sock, &handshake).await?;

    let mut request = Vec::new();
    write_varint(&mut request, 0x00);
    write_packet(&mut sock, &request).await?;

    let body = read_packet(&mut sock).await?;
    let mut cur = &body[..];
    let id = read_varint(&mut cur)?;
    if id != 0x00 {
        return Err(Error::Invalid(format!(
            "unexpected status packet 0x{id:02x}"
        )));
    }
    let json = read_string(&mut cur)?;
    let raw: RawStatus = serde_json::from_str(&json)
        .map_err(|e| Error::Invalid(format!("server sent unreadable status: {e}")))?;

    /* The pong echoes the payload, so a server that answers at all answers
    this; the timing is what we are after, and a server that stops talking
    here still gave us a real status. */
    let mut ping = Vec::new();
    write_varint(&mut ping, 0x01);
    ping.extend_from_slice(&0i64.to_be_bytes());
    let sent = Instant::now();
    let latency = match write_packet(&mut sock, &ping).await {
        Ok(()) => match read_packet(&mut sock).await {
            Ok(_) => sent.elapsed().as_millis() as u32,
            Err(_) => 0,
        },
        Err(_) => 0,
    };

    Ok(ServerStatus {
        online: true,
        motd: raw.description.text(),
        players: raw.players.as_ref().map(|p| p.online).unwrap_or(0),
        max_players: raw.players.as_ref().map(|p| p.max).unwrap_or(0),
        ping_ms: latency,
        version: raw.version.map(|v| v.name).unwrap_or_default(),
        favicon: raw.favicon,
        error: None,
    })
}

/// `host`, `host:port`, or an IPv6 literal in brackets.
fn split_address(address: &str) -> (String, u16) {
    let a = address.trim();
    if let Some(rest) = a.strip_prefix('[') {
        if let Some((host, tail)) = rest.split_once(']') {
            let port = tail.strip_prefix(':').and_then(|p| p.parse().ok());
            return (host.to_string(), port.unwrap_or(DEFAULT_PORT));
        }
    }
    match a.rsplit_once(':') {
        Some((host, port)) if !host.is_empty() => match port.parse() {
            Ok(p) => (host.to_string(), p),
            Err(_) => (a.to_string(), DEFAULT_PORT),
        },
        _ => (a.to_string(), DEFAULT_PORT),
    }
}

// ── wire format ───────────────────────────────────────────────

fn write_varint(buf: &mut Vec<u8>, value: i32) {
    let mut v = value as u32;
    loop {
        let byte = (v & 0x7F) as u8;
        v >>= 7;
        if v == 0 {
            buf.push(byte);
            break;
        }
        buf.push(byte | 0x80);
    }
}

fn write_string(buf: &mut Vec<u8>, s: &str) {
    write_varint(buf, s.len() as i32);
    buf.extend_from_slice(s.as_bytes());
}

async fn write_packet(sock: &mut TcpStream, body: &[u8]) -> Result<()> {
    let mut framed = Vec::with_capacity(body.len() + 5);
    write_varint(&mut framed, body.len() as i32);
    framed.extend_from_slice(body);
    sock.write_all(&framed).await?;
    Ok(())
}

/// A varint on the wire has to be read a byte at a time — its length is in the
/// bytes themselves, so there is nothing to buffer ahead.
async fn read_varint_stream(sock: &mut TcpStream) -> Result<i32> {
    let mut result: i32 = 0;
    for shift in 0..5 {
        let byte = sock.read_u8().await?;
        result |= ((byte & 0x7F) as i32) << (shift * 7);
        if byte & 0x80 == 0 {
            return Ok(result);
        }
    }
    Err(Error::Invalid("varint longer than five bytes".into()))
}

async fn read_packet(sock: &mut TcpStream) -> Result<Vec<u8>> {
    let len = read_varint_stream(sock).await?;
    if !(0..=2_097_152).contains(&len) {
        return Err(Error::Invalid(format!("absurd packet length {len}")));
    }
    let mut body = vec![0u8; len as usize];
    sock.read_exact(&mut body).await?;
    Ok(body)
}

fn read_varint(cur: &mut &[u8]) -> Result<i32> {
    let mut result: i32 = 0;
    for shift in 0..5 {
        let (byte, rest) = cur
            .split_first()
            .ok_or_else(|| Error::Invalid("packet ended early".into()))?;
        *cur = rest;
        result |= ((byte & 0x7F) as i32) << (shift * 7);
        if byte & 0x80 == 0 {
            return Ok(result);
        }
    }
    Err(Error::Invalid("varint longer than five bytes".into()))
}

fn read_string(cur: &mut &[u8]) -> Result<String> {
    let len = read_varint(cur)? as usize;
    if cur.len() < len {
        return Err(Error::Invalid("string ran past the packet".into()));
    }
    let (s, rest) = cur.split_at(len);
    *cur = rest;
    Ok(String::from_utf8_lossy(s).into_owned())
}

// ── the status JSON ───────────────────────────────────────────

#[derive(Debug, Deserialize)]
struct RawStatus {
    #[serde(default)]
    description: Description,
    players: Option<RawPlayers>,
    version: Option<RawVersion>,
    favicon: Option<String>,
}

#[derive(Debug, Deserialize)]
struct RawPlayers {
    online: u32,
    max: u32,
}

#[derive(Debug, Deserialize)]
struct RawVersion {
    name: String,
}

/// The motd is a chat component, and every server writes it differently: a
/// bare string, `{text}`, or a tree of `extra` runs. Flatten it and drop the
/// section-sign colour codes rather than showing them.
#[derive(Debug, Default, Deserialize)]
#[serde(untagged)]
enum Description {
    Text(String),
    Component {
        #[serde(default)]
        text: String,
        #[serde(default)]
        extra: Vec<Description>,
    },
    #[default]
    Empty,
}

impl Description {
    fn text(&self) -> String {
        let mut out = String::new();
        self.walk(&mut out);
        strip_codes(&out)
    }

    fn walk(&self, out: &mut String) {
        match self {
            Self::Text(s) => out.push_str(s),
            Self::Component { text, extra } => {
                out.push_str(text);
                for e in extra {
                    e.walk(out);
                }
            }
            Self::Empty => {}
        }
    }
}

fn strip_codes(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(c) = chars.next() {
        if c == '§' {
            chars.next();
        } else {
            out.push(c);
        }
    }
    out.trim().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn address_forms() {
        assert_eq!(
            split_address("play.example.net"),
            ("play.example.net".into(), 25565)
        );
        assert_eq!(
            split_address("127.0.0.1:25566"),
            ("127.0.0.1".into(), 25566)
        );
        assert_eq!(split_address("[::1]:25567"), ("::1".into(), 25567));
        // a host with a colon but no number is a host, not a parse error
        assert_eq!(split_address("weird:host"), ("weird:host".into(), 25565));
    }

    #[test]
    fn varints_round_trip() {
        for v in [0, 1, 127, 128, 255, 2_097_151, i32::MAX] {
            let mut buf = Vec::new();
            write_varint(&mut buf, v);
            let mut cur = &buf[..];
            assert_eq!(read_varint(&mut cur).unwrap(), v);
            assert!(cur.is_empty());
        }
    }

    #[test]
    fn motd_shapes() {
        let plain: RawStatus = serde_json::from_str(r#"{"description":"hello"}"#).unwrap();
        assert_eq!(plain.description.text(), "hello");

        let component: RawStatus =
            serde_json::from_str(r#"{"description":{"text":"§aEmber","extra":[{"text":" SMP"}]}}"#)
                .unwrap();
        assert_eq!(component.description.text(), "Ember SMP");

        let missing: RawStatus = serde_json::from_str("{}").unwrap();
        assert_eq!(missing.description.text(), "");
    }
}
