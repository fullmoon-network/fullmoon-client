/* obs-websocket v5, reduced to what the clip library needs: get in, ask a
request, and hear the one event that says where the replay landed.

OBS's protocol is JSON over one socket carrying three unrelated message
kinds — Hello/Identified for the handshake, RequestResponse for our asks,
and Events we did not ask for at any particular moment. A response and the
ReplayBufferSaved event can arrive in either order, so events seen while
waiting for a response are kept, not dropped.

Failures are separated because the user's next move differs: the socket
never opening means OBS is closed or the URL is wrong; a rejected identify
means the password is wrong; anything else is OBS answering badly. */
use std::{
    collections::VecDeque,
    sync::atomic::{AtomicU64, Ordering},
    time::Duration,
};

use base64::{engine::general_purpose::STANDARD, Engine as _};
use futures::{SinkExt, StreamExt};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use tokio::net::TcpStream;
use tokio_tungstenite::{connect_async, tungstenite::Message, MaybeTlsStream, WebSocketStream};

const CONNECT_TIMEOUT: Duration = Duration::from_secs(3);
const RESPONSE_TIMEOUT: Duration = Duration::from_secs(6);
const SAVED_TIMEOUT: Duration = Duration::from_secs(12);

type Socket = WebSocketStream<MaybeTlsStream<TcpStream>>;

#[derive(Debug, Clone)]
pub enum Error {
    /// OBS is not there, or not at this address.
    Transport(String),
    /// OBS is there and refused us — the password is wrong, or it needs one.
    Auth(String),
    /// OBS answered, but not the way the protocol says it should.
    Protocol(String),
}

impl std::fmt::Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Transport(m) | Self::Auth(m) | Self::Protocol(m) => f.write_str(m),
        }
    }
}

impl std::error::Error for Error {}

impl From<Error> for crate::error::Error {
    fn from(e: Error) -> Self {
        crate::error::Error::Invalid(e.to_string())
    }
}

/// `base64(sha256(base64(sha256(password + salt)) + challenge))`, the v5
/// authentication string. Pure, so the shape can be tested without OBS.
pub fn auth_string(password: &str, salt: &str, challenge: &str) -> String {
    let secret = sha256_b64(&format!("{password}{salt}"));
    sha256_b64(&format!("{secret}{challenge}"))
}

fn sha256_b64(input: &str) -> String {
    STANDARD.encode(Sha256::digest(input.as_bytes()))
}

static REQUEST_SEQ: AtomicU64 = AtomicU64::new(1);

fn next_request_id() -> String {
    let n = REQUEST_SEQ.fetch_add(1, Ordering::Relaxed);
    format!("{}-{n}", std::process::id())
}

/// The id a RequestResponse answers, when the message is one.
fn response_id(msg: &Value) -> Option<&str> {
    if msg["op"].as_u64() != Some(7) {
        return None;
    }
    msg["d"]["requestId"].as_str()
}

fn event_of(msg: &Value) -> Option<(String, Value)> {
    if msg["op"].as_u64() != Some(5) {
        return None;
    }
    let kind = msg["d"]["eventType"].as_str()?.to_owned();
    Some((kind, msg["d"]["eventData"].clone()))
}

#[derive(Debug)]
pub struct Client {
    ws: Socket,
    /// Events that arrived while we were waiting for a response.
    events: VecDeque<(String, Value)>,
}

impl Client {
    pub async fn connect(url: &str, password: &str) -> Result<Self, Error> {
        let opening = connect_async(url);
        let (mut ws, _) = tokio::time::timeout(CONNECT_TIMEOUT, opening)
            .await
            .map_err(|_| Error::Transport(format!("OBS did not answer at {url} within 3 s")))?
            .map_err(|e| Error::Transport(format!("cannot reach OBS at {url}: {e}")))?;

        let hello = next_json(&mut ws, CONNECT_TIMEOUT).await?;
        if hello["op"].as_u64() != Some(0) {
            return Err(Error::Protocol(
                "OBS did not open the conversation with a Hello".into(),
            ));
        }

        let auth = hello["d"].get("authentication").filter(|a| !a.is_null());
        let mut identify = json!({ "rpcVersion": 1 });
        if let Some(a) = auth {
            let salt = a["salt"].as_str().unwrap_or_default();
            let challenge = a["challenge"].as_str().unwrap_or_default();
            if password.is_empty() {
                return Err(Error::Auth(
                    "OBS requires a password — set it in the clip settings".into(),
                ));
            }
            identify["authentication"] = json!(auth_string(password, salt, challenge));
        }

        let payload = json!({ "op": 1, "d": identify });
        ws.send(Message::Text(payload.to_string()))
            .await
            .map_err(|e| Error::Transport(format!("could not finish the OBS handshake: {e}")))?;

        let identified = next_json(&mut ws, CONNECT_TIMEOUT).await.map_err(|e| {
            if auth.is_some() {
                match e {
                    Error::Transport(m) => Error::Auth(format!(
                        "OBS refused the sign-in — check the password ({m})"
                    )),
                    other => other,
                }
            } else {
                e
            }
        })?;
        if identified["op"].as_u64() != Some(2) {
            return Err(if auth.is_some() {
                Error::Auth("OBS rejected the password".into())
            } else {
                Error::Protocol("OBS did not confirm the connection".into())
            });
        }

        Ok(Self {
            ws,
            events: VecDeque::new(),
        })
    }

    /// One request, matched by id to its own answer. Events that arrive in
    /// between are kept for a later `take_event`.
    pub async fn request(&mut self, request_type: &str, data: Value) -> Result<Value, Error> {
        let id = next_request_id();
        let payload = json!({
            "op": 6,
            "d": { "requestType": request_type, "requestId": id, "requestData": data },
        });
        self.ws
            .send(Message::Text(payload.to_string()))
            .await
            .map_err(|e| Error::Transport(format!("lost the OBS connection: {e}")))?;

        loop {
            let msg = next_json(&mut self.ws, RESPONSE_TIMEOUT).await?;
            if let Some((kind, data)) = event_of(&msg) {
                self.events.push_back((kind, data));
                continue;
            }
            if response_id(&msg) != Some(id.as_str()) {
                continue;
            }
            let status = &msg["d"]["requestStatus"];
            if status["result"].as_bool().unwrap_or(false) {
                return Ok(msg["d"].get("responseData").cloned().unwrap_or(Value::Null));
            }
            let comment = status["comment"]
                .as_str()
                .unwrap_or("OBS refused the request without saying why");
            return Err(Error::Protocol(format!("{request_type}: {comment}")));
        }
    }

    pub async fn replay_active(&mut self) -> Result<bool, Error> {
        let data = self.request("GetReplayBufferStatus", json!({})).await?;
        Ok(data["outputActive"].as_bool().unwrap_or(false))
    }

    /// Ask OBS to save, then learn the file from the ReplayBufferSaved event.
    /// The event is the reliable answer; if it does not arrive, ask once where
    /// the last replay went before giving up.
    pub async fn save_replay_file(&mut self) -> Result<String, Error> {
        self.request("SaveReplayBuffer", json!({})).await?;

        if let Some(data) = self.take_event("ReplayBufferSaved", SAVED_TIMEOUT).await? {
            if let Some(path) = data["savedReplayPath"].as_str() {
                return Ok(path.to_owned());
            }
        }

        let data = self.request("GetLastReplayBufferReplay", json!({})).await?;
        data["savedReplayPath"]
            .as_str()
            .map(str::to_owned)
            .ok_or_else(|| Error::Protocol("OBS did not say where it saved the replay".into()))
    }

    /// The named event, from the ones already seen or the next ones to arrive.
    /// `Ok(None)` means the wait ran out; the caller decides whether that is an
    /// error.
    async fn take_event(&mut self, name: &str, wait: Duration) -> Result<Option<Value>, Error> {
        if let Some(index) = self.events.iter().position(|(kind, _)| kind == name) {
            return Ok(self.events.remove(index).map(|(_, data)| data));
        }

        let deadline = tokio::time::Instant::now() + wait;
        loop {
            let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
            if remaining.is_zero() {
                return Ok(None);
            }
            let msg = match next_json(&mut self.ws, remaining).await {
                Ok(m) => m,
                // the fallback request will report a socket that is really gone
                Err(Error::Transport(_)) => return Ok(None),
                Err(e) => return Err(e),
            };
            if let Some((kind, data)) = event_of(&msg) {
                if kind == name {
                    return Ok(Some(data));
                }
                self.events.push_back((kind, data));
            }
        }
    }
}

async fn next_json(ws: &mut Socket, wait: Duration) -> Result<Value, Error> {
    let deadline = tokio::time::Instant::now() + wait;
    loop {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        if remaining.is_zero() {
            return Err(Error::Transport("OBS stopped answering".into()));
        }
        let msg = tokio::time::timeout_at(deadline, ws.next())
            .await
            .map_err(|_| Error::Transport("OBS stopped answering".into()))?
            .ok_or_else(|| Error::Transport("OBS closed the connection".into()))?
            .map_err(|e| Error::Transport(format!("OBS connection failed: {e}")))?;

        match msg {
            Message::Text(text) => {
                return serde_json::from_str(&text)
                    .map_err(|e| Error::Protocol(format!("OBS sent invalid JSON: {e}")))
            }
            Message::Binary(bytes) => {
                return serde_json::from_slice(&bytes)
                    .map_err(|e| Error::Protocol(format!("OBS sent invalid JSON: {e}")))
            }
            Message::Ping(payload) => {
                let _ = ws.send(Message::Pong(payload)).await;
            }
            Message::Close(_) => return Err(Error::Transport("OBS closed the connection".into())),
            _ => {}
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_auth_string_is_a_hash_and_never_the_password() {
        let out = auth_string("hunter2", "saltysalt", "challenge-token");
        assert_eq!(STANDARD.decode(&out).expect("base64").len(), 32);
        assert!(!out.contains("hunter2"));
        assert!(!out.contains("saltysalt"));
        assert!(!out.contains("challenge-token"));
    }

    #[test]
    fn every_input_changes_the_auth_string() {
        let base = auth_string("password", "salt", "challenge");
        assert_ne!(base, auth_string("password2", "salt", "challenge"));
        assert_ne!(base, auth_string("password", "salt2", "challenge"));
        assert_ne!(base, auth_string("password", "salt", "challenge2"));
    }

    #[test]
    fn a_response_only_answers_the_id_it_names() {
        let msg: Value = serde_json::from_str(
            r#"{"op":7,"d":{"requestType":"GetReplayBufferStatus","requestId":"req-42",
                "requestStatus":{"result":true,"code":100},"responseData":{"outputActive":true}}}"#,
        )
        .unwrap();

        assert_eq!(response_id(&msg), Some("req-42"));
        assert_ne!(response_id(&msg), Some("req-41"));
        assert!(event_of(&msg).is_none());
    }

    #[test]
    fn an_event_is_not_mistaken_for_a_response() {
        let msg: Value = serde_json::from_str(
            r#"{"op":5,"d":{"eventType":"ReplayBufferSaved","eventIntent":4,
                "eventData":{"savedReplayPath":"/tmp/x.mkv"}}}"#,
        )
        .unwrap();

        assert!(response_id(&msg).is_none());
        let (kind, data) = event_of(&msg).expect("event");
        assert_eq!(kind, "ReplayBufferSaved");
        assert_eq!(data["savedReplayPath"], "/tmp/x.mkv");
    }

    #[test]
    fn request_ids_do_not_repeat() {
        let a = next_request_id();
        let b = next_request_id();
        assert_ne!(a, b);
    }

    type TestSocket = tokio_tungstenite::WebSocketStream<tokio::net::TcpStream>;

    async fn read_json(ws: &mut TestSocket) -> Value {
        loop {
            match ws.next().await.expect("socket").expect("frame") {
                Message::Text(text) => return serde_json::from_str(&text).expect("json"),
                Message::Ping(payload) => ws.send(Message::Pong(payload)).await.expect("pong"),
                _ => {}
            }
        }
    }

    async fn send_json(ws: &mut TestSocket, value: Value) {
        ws.send(Message::Text(value.to_string())).await.expect("send");
    }

    fn response(id: &Value, data: Value) -> Value {
        json!({"op": 7, "d": {
            "requestType": "x", "requestId": id,
            "requestStatus": {"result": true, "code": 100},
            "responseData": data,
        }})
    }

    /// The handshake, a status request, and a save whose event beats its own
    /// response. This is the whole protocol path the library depends on, run
    /// against a socket we control rather than a claim that it should work.
    #[tokio::test]
    async fn authenticates_and_saves_against_a_fake_obs() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut ws = tokio_tungstenite::accept_async(stream).await.unwrap();
            send_json(&mut ws, json!({"op": 0, "d": {
                "obsWebSocketVersion": "5.5.2", "rpcVersion": 1,
                "authentication": {"challenge": "chal", "salt": "salt"},
            }})).await;

            let identify = read_json(&mut ws).await;
            assert_eq!(identify["op"], 1);
            assert_eq!(identify["d"]["rpcVersion"], 1);
            assert_eq!(identify["d"]["authentication"], auth_string("pw", "salt", "chal"));
            send_json(&mut ws, json!({"op": 2, "d": {"negotiatedRpcVersion": 1}})).await;

            let status = read_json(&mut ws).await;
            assert_eq!(status["d"]["requestType"], "GetReplayBufferStatus");
            let id = status["d"]["requestId"].clone();
            send_json(&mut ws, response(&id, json!({"outputActive": true}))).await;

            let save = read_json(&mut ws).await;
            assert_eq!(save["d"]["requestType"], "SaveReplayBuffer");
            // the event goes out first: the client must keep it while it waits
            send_json(&mut ws, json!({"op": 5, "d": {
                "eventType": "ReplayBufferSaved", "eventIntent": 4,
                "eventData": {"savedReplayPath": "/tmp/fake-replay.mkv"},
            }})).await;
            let id = save["d"]["requestId"].clone();
            send_json(&mut ws, response(&id, json!({}))).await;
        });

        let mut client = Client::connect(&format!("ws://{address}"), "pw").await.unwrap();
        assert!(client.replay_active().await.unwrap());
        assert_eq!(
            client.save_replay_file().await.unwrap(),
            "/tmp/fake-replay.mkv"
        );
        server.await.unwrap();
    }

    #[tokio::test]
    async fn a_refused_identify_is_reported_as_a_password_problem() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut ws = tokio_tungstenite::accept_async(stream).await.unwrap();
            send_json(&mut ws, json!({"op": 0, "d": {
                "rpcVersion": 1,
                "authentication": {"challenge": "chal", "salt": "salt"},
            }})).await;
            let _identify = read_json(&mut ws).await;
            let _ = ws.close(None).await;
        });

        let error = Client::connect(&format!("ws://{address}"), "wrong")
            .await
            .expect_err("the fake OBS closed the connection");
        assert!(matches!(error, Error::Auth(_)), "{error:?}");
    }
}
