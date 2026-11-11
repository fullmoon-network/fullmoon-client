/* Microsoft sign-in. Every Minecraft launcher has to walk the same chain:

  MSA token → Xbox Live → XSTS → Minecraft services → profile

Two ways in, because they fail in different places. The device code flow
needs no local port and works when the browser is on another machine; the
auth-code flow is one click when it is the same machine, and uses a loopback
redirect with PKCE so no client secret has to exist. Both end in the same
Account.

Tokens never cross the IPC boundary. The UI is handed a profile; the session
material lives in `sessions.json` and only the launch path reads it. */
use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use tokio::io::{AsyncReadExt, AsyncWriteExt};

use crate::{
    error::{Error, Result},
    model::Account,
    paths, store,
};

const BRAND_CLIENT_ID: &str = env!("PINION_MS_CLIENT_ID");
const DEVICE_CODE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const AUTHORIZE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const SCOPE: &str = "XboxLive.signin offline_access";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

/// The Azure application the sign-in runs as. A fork has to register its own —
/// Microsoft ties the Xbox Live scope to the application, so there is no id
/// that can be shipped here and still be honest. The env var exists so a
/// developer can try one without a rebuild.
pub fn client_id() -> Result<String> {
    if let Ok(v) = std::env::var("PINION_MS_CLIENT_ID") {
        if !v.trim().is_empty() {
            return Ok(v.trim().to_string());
        }
    }
    if BRAND_CLIENT_ID.trim().is_empty() {
        return Err(Error::Invalid(
            "no Microsoft application id is configured. Register an Azure app (public client, \
             redirect URI http://localhost, delegated scope XboxLive.signin) and put its id in \
             brand.json as \"msClientId\", or set PINION_MS_CLIENT_ID"
                .into(),
        ));
    }
    Ok(BRAND_CLIENT_ID.trim().to_string())
}

/* ── stored sessions ───────────────────────────────────────────── */

/// What the game needs and the UI must never see.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Session {
    pub uuid: String,
    pub access_token: String,
    /// absent on an imported session — the official launcher keeps its own
    #[serde(default)]
    pub refresh_token: Option<String>,
    #[serde(default)]
    pub xuid: Option<String>,
    /// epoch millis; 0 when the source did not say
    #[serde(default)]
    pub expires_at: i64,
}

impl Session {
    pub fn is_expired(&self) -> bool {
        // a minute of slack: a token that dies mid-handshake reads as a ban
        self.expires_at != 0 && self.expires_at - 60_000 < now_millis()
    }
}

pub fn now_millis() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

type SessionMap = BTreeMap<String, Session>;

pub async fn session(uuid: &str) -> Option<Session> {
    let map: SessionMap = store::read_or(&paths::sessions_file(), BTreeMap::new).await;
    map.get(uuid).cloned()
}

async fn put_session(s: Session) -> Result<()> {
    let mut map: SessionMap = store::read_or(&paths::sessions_file(), BTreeMap::new).await;
    map.insert(s.uuid.clone(), s);
    store::write(&paths::sessions_file(), &map).await
}

pub async fn forget_session(uuid: &str) -> Result<()> {
    let mut map: SessionMap = store::read_or(&paths::sessions_file(), BTreeMap::new).await;
    if map.remove(uuid).is_some() {
        store::write(&paths::sessions_file(), &map).await?;
    }
    Ok(())
}

/* ── the MSA half ──────────────────────────────────────────────── */

#[derive(Debug, Deserialize)]
struct MsaToken {
    access_token: String,
    #[serde(default)]
    refresh_token: Option<String>,
}

#[derive(Debug, Deserialize)]
struct OauthError {
    error: String,
    #[serde(default)]
    error_description: Option<String>,
}

/// One place where an MSA reply becomes either a token or a sentence a user
/// can act on.
async fn read_token(res: reqwest::Response) -> Result<std::result::Result<MsaToken, OauthError>> {
    let status = res.status();
    let body = res.text().await?;
    if status.is_success() {
        return Ok(Ok(serde_json::from_str(&body)?));
    }
    match serde_json::from_str::<OauthError>(&body) {
        Ok(e) => Ok(Err(e)),
        Err(_) => Err(Error::Invalid(format!(
            "Microsoft replied {status}: {body}"
        ))),
    }
}

#[derive(Debug, Clone)]
pub struct DeviceFlow {
    pub device_code: String,
    /// Microsoft's own pacing, in seconds, raised whenever it says slow_down.
    /// The UI polls on its own timer; this is what actually keeps the request
    /// rate legal.
    pub interval: i64,
    pub next_poll_at: i64,
    pub expires_at: i64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceCodePrompt {
    pub session: String,
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in: i64,
}

#[derive(Debug, Deserialize)]
struct DeviceCodeReply {
    device_code: String,
    user_code: String,
    verification_uri: String,
    expires_in: i64,
    #[serde(default = "default_interval")]
    interval: i64,
}

fn default_interval() -> i64 {
    5
}

pub async fn begin_device_code(client: &reqwest::Client) -> Result<(DeviceCodePrompt, DeviceFlow)> {
    let cid = client_id()?;
    let res = client
        .post(DEVICE_CODE_URL)
        .form(&[("client_id", cid.as_str()), ("scope", SCOPE)])
        .send()
        .await?;
    let status = res.status();
    let body = res.text().await?;
    if !status.is_success() {
        let hint = serde_json::from_str::<OauthError>(&body)
            .map(|e| e.error_description.unwrap_or(e.error))
            .unwrap_or(body);
        return Err(Error::Invalid(format!(
            "Microsoft refused the sign-in request: {hint}"
        )));
    }
    let reply: DeviceCodeReply = serde_json::from_str(&body)?;
    let key = format!("dc-{}", uuid::Uuid::new_v4().simple());
    let flow = DeviceFlow {
        device_code: reply.device_code,
        interval: reply.interval.max(1),
        next_poll_at: now_millis(),
        expires_at: now_millis() + reply.expires_in * 1000,
    };
    Ok((
        DeviceCodePrompt {
            session: key,
            user_code: reply.user_code,
            verification_uri: reply.verification_uri,
            expires_in: reply.expires_in,
        },
        flow,
    ))
}

/// `Ok(None)` means the user has not finished in the browser yet — the only
/// non-error way this call ends without an account.
pub async fn poll_device(
    client: &reqwest::Client,
    flow: &mut DeviceFlow,
) -> Result<Option<Account>> {
    if now_millis() > flow.expires_at {
        return Err(Error::Invalid(
            "the sign-in code expired — start again".into(),
        ));
    }
    // the UI's timer is a suggestion; Microsoft's interval is not
    let wait = flow.next_poll_at - now_millis();
    if wait > 0 {
        tokio::time::sleep(std::time::Duration::from_millis(wait as u64)).await;
    }
    let cid = client_id()?;
    let res = client
        .post(TOKEN_URL)
        .form(&[
            ("client_id", cid.as_str()),
            ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
            ("device_code", flow.device_code.as_str()),
        ])
        .send()
        .await?;

    flow.next_poll_at = now_millis() + flow.interval * 1000;
    match read_token(res).await? {
        Ok(token) => Ok(Some(finish(client, token, "microsoft").await?)),
        Err(e) => match e.error.as_str() {
            "slow_down" => {
                flow.interval += 5;
                flow.next_poll_at = now_millis() + flow.interval * 1000;
                Ok(None)
            }
            "authorization_pending" => Ok(None),
            "authorization_declined" => Err(Error::Invalid("the sign-in was declined".into())),
            "expired_token" => Err(Error::Invalid(
                "the sign-in code expired — start again".into(),
            )),
            other => Err(Error::Invalid(
                e.error_description.unwrap_or_else(|| other.to_string()),
            )),
        },
    }
}

/* ── the auth-code half, over a loopback redirect ──────────────── */

fn percent(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char)
            }
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

fn b64url(bytes: &[u8]) -> String {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    let mut out = String::new();
    for chunk in bytes.chunks(3) {
        let b = [
            chunk[0],
            *chunk.get(1).unwrap_or(&0),
            *chunk.get(2).unwrap_or(&0),
        ];
        let n = ((b[0] as u32) << 16) | ((b[1] as u32) << 8) | b[2] as u32;
        let idx = [n >> 18 & 63, n >> 12 & 63, n >> 6 & 63, n & 63];
        for (i, x) in idx.iter().enumerate() {
            if i <= chunk.len() {
                out.push(TABLE[*x as usize] as char);
            }
        }
    }
    out
}

fn random_token() -> String {
    // two v4 uuids of entropy, in the character set PKCE allows
    format!(
        "{}{}",
        uuid::Uuid::new_v4().simple(),
        uuid::Uuid::new_v4().simple()
    )
}

/// The page the browser lands on. It is the only HTML the launcher serves, and
/// it exists so the user knows the tab is done.
fn done_page(title: &str, body: &str) -> String {
    let html = format!(
        "<!doctype html><meta charset=utf-8><title>{title}</title>\
         <style>html{{background:#141210;color:#EDE6DF;font:16px/1.6 system-ui,sans-serif;\
         display:grid;place-items:center;height:100%}}div{{text-align:center}}\
         b{{color:#B0481A;letter-spacing:.14em;text-transform:uppercase;font-size:13px}}\
         p{{opacity:.68;font-size:14px}}</style>\
         <div><b>{}</b><h1>{title}</h1><p>{body}</p></div>",
        crate::paths::BRAND_NAME
    );
    format!(
        "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\n\
         Connection: close\r\n\r\n{html}",
        html.len()
    )
}

fn query_of(request_line: &str) -> BTreeMap<String, String> {
    let mut out = BTreeMap::new();
    let Some(target) = request_line.split_whitespace().nth(1) else {
        return out;
    };
    let Some((_, query)) = target.split_once('?') else {
        return out;
    };
    for pair in query.split('&') {
        if let Some((k, v)) = pair.split_once('=') {
            out.insert(k.to_string(), unpercent(v));
        }
    }
    out
}

fn unpercent(s: &str) -> String {
    let bytes = s.replace('+', " ").into_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let Ok(b) = u8::from_str_radix(&String::from_utf8_lossy(&bytes[i + 1..i + 3]), 16) {
                out.push(b);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

/// Opens the system browser and answers when the redirect comes back. The
/// listener is bound before the browser is opened, so the port in the redirect
/// URI is always the one being listened on.
pub async fn login_authcode(client: &reqwest::Client) -> Result<Account> {
    let cid = client_id()?;
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await?;
    let port = listener.local_addr()?.port();
    let redirect = format!("http://localhost:{port}/");

    let verifier = random_token();
    let challenge = {
        use sha2::{Digest, Sha256};
        b64url(&Sha256::digest(verifier.as_bytes()))
    };
    let state = random_token();

    let url = format!(
        "{AUTHORIZE_URL}?client_id={cid}&response_type=code&redirect_uri={}&response_mode=query\
         &scope={}&code_challenge={challenge}&code_challenge_method=S256&state={state}\
         &prompt=select_account",
        percent(&redirect),
        percent(SCOPE)
    );
    tauri_plugin_opener::open_url(&url, None::<&str>)
        .map_err(|e| Error::Invalid(format!("could not open the browser: {e}")))?;

    let code = tokio::time::timeout(std::time::Duration::from_secs(300), async {
        loop {
            let (mut sock, _) = listener.accept().await?;
            let mut buf = vec![0u8; 8192];
            let n = sock.read(&mut buf).await?;
            let head = String::from_utf8_lossy(&buf[..n]).into_owned();
            let line = head.lines().next().unwrap_or_default().to_string();
            let q = query_of(&line);

            if let Some(err) = q.get("error") {
                let msg = q
                    .get("error_description")
                    .cloned()
                    .unwrap_or_else(|| err.clone());
                let _ = sock
                    .write_all(done_page("Sign-in failed", &msg).as_bytes())
                    .await;
                let _ = sock.shutdown().await;
                return Err(Error::Invalid(msg));
            }
            let Some(got) = q.get("code") else {
                // favicon and friends: answer and keep waiting for the real one
                let _ = sock
                    .write_all(done_page("Waiting", "This tab can stay open.").as_bytes())
                    .await;
                let _ = sock.shutdown().await;
                continue;
            };
            if q.get("state").map(String::as_str) != Some(state.as_str()) {
                let _ = sock
                    .write_all(
                        done_page("Sign-in failed", "The reply did not match this request.")
                            .as_bytes(),
                    )
                    .await;
                let _ = sock.shutdown().await;
                return Err(Error::Invalid(
                    "the browser reply did not match this sign-in request".into(),
                ));
            }
            let _ = sock
                .write_all(
                    done_page(
                        "Signed in",
                        "You can close this tab and go back to the launcher.",
                    )
                    .as_bytes(),
                )
                .await;
            let _ = sock.shutdown().await;
            return Ok(got.clone());
        }
    })
    .await
    .map_err(|_| Error::Invalid("the browser sign-in timed out".into()))??;

    let res = client
        .post(TOKEN_URL)
        .form(&[
            ("client_id", cid.as_str()),
            ("grant_type", "authorization_code"),
            ("code", code.as_str()),
            ("redirect_uri", redirect.as_str()),
            ("code_verifier", verifier.as_str()),
        ])
        .send()
        .await?;
    match read_token(res).await? {
        Ok(token) => finish(client, token, "microsoft").await,
        Err(e) => Err(Error::Invalid(
            e.error_description.unwrap_or_else(|| e.error.clone()),
        )),
    }
}

/* ── Xbox Live → XSTS → Minecraft ──────────────────────────────── */

#[derive(Debug, Deserialize)]
struct XboxReply {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    display_claims: DisplayClaims,
}

#[derive(Debug, Deserialize)]
struct DisplayClaims {
    #[serde(default)]
    xui: Vec<XuiClaim>,
}

#[derive(Debug, Deserialize)]
struct XuiClaim {
    #[serde(default)]
    uhs: Option<String>,
    #[serde(default)]
    xid: Option<String>,
}

async fn xbox_live(client: &reqwest::Client, msa: &str) -> Result<XboxReply> {
    let body = serde_json::json!({
        "Properties": {
            "AuthMethod": "RPS",
            "SiteName": "user.auth.xboxlive.com",
            "RpsTicket": format!("d={msa}"),
        },
        "RelyingParty": "http://auth.xboxlive.com",
        "TokenType": "JWT",
    });
    let res = client
        .post(XBL_URL)
        .header("Accept", "application/json")
        .json(&body)
        .send()
        .await?;
    if !res.status().is_success() {
        let status = res.status();
        return Err(Error::Invalid(format!(
            "Xbox Live refused the token ({status})"
        )));
    }
    Ok(res.json().await?)
}

async fn xsts(client: &reqwest::Client, xbl: &str) -> Result<XboxReply> {
    let body = serde_json::json!({
        "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl] },
        "RelyingParty": "rp://api.minecraftservices.com/",
        "TokenType": "JWT",
    });
    let res = client
        .post(XSTS_URL)
        .header("Accept", "application/json")
        .json(&body)
        .send()
        .await?;
    if res.status().is_success() {
        return Ok(res.json().await?);
    }

    // XSTS says why in a numeric code, and every one of them means something
    // the user has to go and do somewhere else
    let raw: serde_json::Value = res.json().await.unwrap_or_default();
    let xerr = raw.get("XErr").and_then(|v| v.as_i64()).unwrap_or(0);
    Err(Error::Invalid(match xerr {
        2148916233 => {
            "this Microsoft account has no Xbox profile — create one at xbox.com and sign in again"
                .into()
        }
        2148916235 => "Xbox Live is not available in this account's country".into(),
        2148916236 | 2148916237 => {
            "this account needs adult verification before it can use Xbox Live".into()
        }
        2148916238 => "this is a child account — add it to a family group before signing in".into(),
        other => format!("Xbox Live rejected the sign-in (XErr {other})"),
    }))
}

#[derive(Debug, Deserialize)]
struct McToken {
    access_token: String,
    #[serde(default)]
    expires_in: i64,
}

#[derive(Debug, Deserialize)]
struct McProfile {
    id: String,
    name: String,
    #[serde(default)]
    skins: Vec<McTexture>,
    #[serde(default)]
    capes: Vec<McTexture>,
}

#[derive(Debug, Deserialize)]
struct McTexture {
    #[serde(default)]
    state: String,
    #[serde(default)]
    url: String,
    #[serde(default)]
    alias: Option<String>,
}

/// hyphenless as Mojang serves it → the shape the rest of the app uses
fn dashed(id: &str) -> String {
    match uuid::Uuid::parse_str(id) {
        Ok(u) => u.to_string(),
        Err(_) => id.to_string(),
    }
}

async fn minecraft_token(client: &reqwest::Client, uhs: &str, xsts: &str) -> Result<McToken> {
    let res = client
        .post(MC_LOGIN_URL)
        .json(&serde_json::json!({ "identityToken": format!("XBL3.0 x={uhs};{xsts}") }))
        .send()
        .await?;
    if !res.status().is_success() {
        let status = res.status();
        return Err(Error::Invalid(format!(
            "Minecraft services refused the Xbox token ({status})"
        )));
    }
    Ok(res.json().await?)
}

async fn minecraft_profile(client: &reqwest::Client, token: &str) -> Result<McProfile> {
    let res = client.get(MC_PROFILE_URL).bearer_auth(token).send().await?;
    if res.status() == reqwest::StatusCode::NOT_FOUND {
        return Err(Error::Invalid(
            "this account does not own Minecraft: Java Edition".into(),
        ));
    }
    if !res.status().is_success() {
        let status = res.status();
        return Err(Error::Invalid(format!(
            "the Minecraft profile call failed ({status})"
        )));
    }
    Ok(res.json().await?)
}

/// MSA token in, stored account out. Shared by every way of signing in so the
/// account a user ends up with never depends on which button they pressed.
async fn finish(client: &reqwest::Client, msa: MsaToken, source: &str) -> Result<Account> {
    let xbl = xbox_live(client, &msa.access_token).await?;
    let uhs = xbl
        .display_claims
        .xui
        .first()
        .and_then(|c| c.uhs.clone())
        .ok_or_else(|| Error::Invalid("Xbox Live returned no user hash".into()))?;
    let xsts = xsts(client, &xbl.token).await?;
    let xuid = xsts.display_claims.xui.first().and_then(|c| c.xid.clone());
    let mc = minecraft_token(client, &uhs, &xsts.token).await?;
    let profile = minecraft_profile(client, &mc.access_token).await?;

    let account = account_from(&profile, source);
    put_session(Session {
        uuid: account.uuid.clone(),
        access_token: mc.access_token,
        refresh_token: msa.refresh_token,
        xuid,
        expires_at: now_millis() + mc.expires_in.max(0) * 1000,
    })
    .await?;
    upsert(&account).await?;
    Ok(account)
}

fn account_from(profile: &McProfile, source: &str) -> Account {
    let uuid = dashed(&profile.id);
    let hue_seed = uuid::Uuid::parse_str(&uuid)
        .map(|u| u.as_u128())
        .unwrap_or(0);
    Account {
        skin_hue: (hue_seed % 360) as u16,
        skin_url: profile
            .skins
            .iter()
            .find(|s| s.state.eq_ignore_ascii_case("ACTIVE"))
            .map(|s| s.url.clone()),
        capes: profile
            .capes
            .iter()
            .filter(|c| c.state.eq_ignore_ascii_case("ACTIVE"))
            .filter_map(|c| c.alias.clone())
            .collect(),
        username: profile.name.clone(),
        source: source.to_string(),
        uuid,
    }
}

/// Add or replace by uuid, keeping the order the user sees.
async fn upsert(account: &Account) -> Result<()> {
    let mut accounts: Vec<Account> = store::read_or(&paths::accounts_file(), Vec::new).await;
    match accounts.iter_mut().find(|a| a.uuid == account.uuid) {
        Some(slot) => *slot = account.clone(),
        None => accounts.push(account.clone()),
    }
    store::write(&paths::accounts_file(), &accounts).await
}

/* ── refresh ───────────────────────────────────────────────────── */

pub async fn refresh(client: &reqwest::Client, uuid: &str) -> Result<Account> {
    let stored = session(uuid).await.ok_or_else(|| {
        Error::Invalid("this account has no stored session — sign in again".into())
    })?;
    let refresh_token = stored.refresh_token.ok_or_else(|| {
        Error::Invalid(
            "this account was imported, so it has no refresh token — its session ends when the \
             official launcher's does"
                .into(),
        )
    })?;
    let cid = client_id()?;
    let res = client
        .post(TOKEN_URL)
        .form(&[
            ("client_id", cid.as_str()),
            ("grant_type", "refresh_token"),
            ("refresh_token", refresh_token.as_str()),
            ("scope", SCOPE),
        ])
        .send()
        .await?;
    match read_token(res).await? {
        Ok(token) => finish(client, token, "microsoft").await,
        Err(e) => Err(Error::Invalid(format!(
            "the stored sign-in could not be renewed: {}",
            e.error_description.unwrap_or(e.error)
        ))),
    }
}

/// Called on the launch path: a session that can be renewed silently is, and
/// one that cannot is reported before the game is spawned rather than after
/// the user is staring at a multiplayer error.
pub async fn session_for_launch(
    client: &reqwest::Client,
    account: &Account,
) -> Result<Option<Session>> {
    if account.source == "offline" {
        return Ok(None);
    }
    let Some(current) = session(&account.uuid).await else {
        return Err(Error::Invalid(if account.source == "imported" {
            format!(
                "the official launcher did not hand over a usable session for {} — sign in with \
                 Microsoft here to use this account",
                account.username
            )
        } else {
            format!("{} has no session — sign in again", account.username)
        }));
    };
    if !current.is_expired() {
        return Ok(Some(current));
    }
    refresh(client, &account.uuid).await?;
    Ok(session(&account.uuid).await)
}

/* ── importing the official launcher's accounts ────────────────── */

#[derive(Debug, Deserialize)]
struct OfficialFile {
    #[serde(default)]
    accounts: BTreeMap<String, OfficialAccount>,
}

#[derive(Debug, Deserialize)]
struct OfficialAccount {
    #[serde(default, rename = "accessToken")]
    access_token: Option<String>,
    #[serde(default, rename = "accessTokenExpiresAt")]
    expires_at: Option<String>,
    #[serde(default, rename = "minecraftProfile")]
    profile: Option<OfficialProfile>,
}

#[derive(Debug, Deserialize)]
struct OfficialProfile {
    id: String,
    name: String,
}

/// Both spellings the official launcher uses: the classic install writes
/// `launcher_accounts.json`, the Microsoft Store build writes its own file
/// beside it.
fn official_files() -> Vec<std::path::PathBuf> {
    let Some(roaming) = dirs::data_dir() else {
        return Vec::new();
    };
    let mc = roaming.join(".minecraft");
    [
        "launcher_accounts.json",
        "launcher_accounts_microsoft_store.json",
    ]
    .iter()
    .map(|n| mc.join(n))
    .filter(|p| p.exists())
    .collect()
}

fn millis_of(iso: &str) -> i64 {
    time::OffsetDateTime::parse(iso, &time::format_description::well_known::Rfc3339)
        .map(|t| (t.unix_timestamp_nanos() / 1_000_000) as i64)
        .unwrap_or(0)
}

/// Imports the profiles, and their live session where one is present. The
/// Microsoft Store build of the official launcher keeps its token elsewhere and
/// leaves `accessToken` empty — those accounts come across as profiles with no
/// session, which is exactly what they are.
pub async fn import_official() -> Result<Vec<Account>> {
    let mut added = Vec::new();
    let existing: Vec<Account> = store::read_or(&paths::accounts_file(), Vec::new).await;

    for file in official_files() {
        let Ok(bytes) = tokio::fs::read(&file).await else {
            continue;
        };
        let Ok(parsed) = serde_json::from_slice::<OfficialFile>(&bytes) else {
            continue;
        };
        for entry in parsed.accounts.values() {
            let Some(profile) = entry.profile.as_ref() else {
                continue;
            };
            let uuid = dashed(&profile.id);
            if existing.iter().any(|a| a.uuid == uuid)
                || added.iter().any(|a: &Account| a.uuid == uuid)
            {
                continue;
            }
            let account = account_from(
                &McProfile {
                    id: profile.id.clone(),
                    name: profile.name.clone(),
                    skins: Vec::new(),
                    capes: Vec::new(),
                },
                "imported",
            );
            let token = entry.access_token.clone().unwrap_or_default();
            if !token.trim().is_empty() {
                put_session(Session {
                    uuid: account.uuid.clone(),
                    access_token: token,
                    refresh_token: None,
                    xuid: None,
                    expires_at: entry.expires_at.as_deref().map(millis_of).unwrap_or(0),
                })
                .await?;
            }
            upsert(&account).await?;
            added.push(account);
        }
    }
    Ok(added)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn base64url_matches_rfc_vectors() {
        assert_eq!(b64url(b"f"), "Zg");
        assert_eq!(b64url(b"fo"), "Zm8");
        assert_eq!(b64url(b"foo"), "Zm9v");
        assert_eq!(b64url(b"foobar"), "Zm9vYmFy");
        assert_eq!(b64url(&[251, 255]), "-_8");
    }

    #[test]
    fn callback_query_is_parsed_and_unescaped() {
        let q = query_of("GET /?code=abc%2Ddef&state=xyz HTTP/1.1");
        assert_eq!(q.get("code").unwrap(), "abc-def");
        assert_eq!(q.get("state").unwrap(), "xyz");
    }

    #[test]
    fn redirect_uri_survives_percent_encoding() {
        assert_eq!(
            percent("http://localhost:1/"),
            "http%3A%2F%2Flocalhost%3A1%2F"
        );
        assert_eq!(
            percent("XboxLive.signin offline_access"),
            "XboxLive.signin%20offline_access"
        );
    }

    #[test]
    fn the_branded_client_id_is_a_uuid() {
        let id = BRAND_CLIENT_ID.trim();
        assert!(
            !id.is_empty(),
            "msClientId must be set in brand.json so a clean install can sign in with Microsoft",
        );
        let parsed =
            uuid::Uuid::parse_str(id).expect("msClientId must be an Application (client) ID");
        assert!(
            !parsed.is_nil(),
            "msClientId must not be the docs placeholder UUID"
        );
    }

    #[tokio::test]
    #[ignore = "hits login.microsoftonline.com"]
    async fn microsoft_issues_a_device_code_for_the_branded_app() {
        let client = reqwest::Client::new();
        let (prompt, flow) = begin_device_code(&client)
            .await
            .expect("Microsoft should accept the branded public client");
        assert!(
            !prompt.user_code.is_empty(),
            "Microsoft returned an empty user code"
        );
        assert!(
            prompt.verification_uri.contains("microsoft.com")
                || prompt.verification_uri.contains("aka.ms"),
            "unexpected verification uri {}",
            prompt.verification_uri
        );
        assert!(!flow.device_code.is_empty());
    }

    #[test]
    fn an_expiry_in_the_past_is_expired_and_zero_never_is() {
        let base = Session {
            uuid: "u".into(),
            access_token: "t".into(),
            refresh_token: None,
            xuid: None,
            expires_at: 0,
        };
        assert!(!base.is_expired());
        assert!(Session {
            expires_at: now_millis() - 1,
            ..base.clone()
        }
        .is_expired());
        assert!(!Session {
            expires_at: now_millis() + 3_600_000,
            ..base
        }
        .is_expired());
    }
}
