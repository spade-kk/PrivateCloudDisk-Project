//! LSP transport adapters: stdio, TCP, Unix Domain Socket and WebSocket.
//!
//! `run_framed` owns only Content-Length framing. `LspSession` owns JSON-RPC,
//! authentication and documents, so all transports have identical semantics.

use crate::{
    auth::AuthContext,
    protocol::{parse_request, MAX_MESSAGE_BYTES},
    server::{LanguageServer, LspSession},
};
use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    http::HeaderMap,
    response::Response,
    routing::get,
    Router,
};
use futures_util::{SinkExt, StreamExt};
use std::{io, net::SocketAddr, path::Path};
use tokio::{
    io::{AsyncBufReadExt, AsyncRead, AsyncWrite, AsyncWriteExt, BufReader, BufWriter},
    net::TcpListener,
};

pub async fn serve_stdio(server: LanguageServer, auth: AuthContext) -> io::Result<()> {
    let session = server.session(auth);
    run_framed(tokio::io::stdin(), tokio::io::stdout(), session).await
}

pub async fn serve_tcp(
    server: LanguageServer,
    address: SocketAddr,
    auth: AuthContext,
) -> io::Result<()> {
    let listener = TcpListener::bind(address).await?;
    loop {
        let (stream, _) = listener.accept().await?;
        let session = server.session(auth.clone());
        tokio::spawn(async move {
            let (read, write) = tokio::io::split(stream);
            let _ = run_framed(read, write, session).await;
        });
    }
}

#[cfg(unix)]
pub async fn serve_unix(server: LanguageServer, path: &Path, auth: AuthContext) -> io::Result<()> {
    use std::os::unix::fs::PermissionsExt;
    use tokio::net::UnixListener;
    // Avoid deleting an arbitrary caller-supplied path. Deployment tooling can
    // remove an intentionally stale socket after checking ownership.
    if path.exists() {
        return Err(io::Error::new(
            io::ErrorKind::AlreadyExists,
            format!("Unix Socket 已存在：{}", path.display()),
        ));
    }
    let listener = UnixListener::bind(path)?;
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600))?;
    loop {
        let (stream, _) = listener.accept().await?;
        let session = server.session(auth.clone());
        tokio::spawn(async move {
            let (read, write) = tokio::io::split(stream);
            let _ = run_framed(read, write, session).await;
        });
    }
}

#[cfg(not(unix))]
pub async fn serve_unix(
    _server: LanguageServer,
    _path: &Path,
    _auth: AuthContext,
) -> io::Result<()> {
    Err(io::Error::new(
        io::ErrorKind::Unsupported,
        "当前操作系统不支持 Unix Domain Socket",
    ))
}

pub async fn serve_websocket(
    server: LanguageServer,
    address: SocketAddr,
    auth: AuthContext,
) -> io::Result<()> {
    let state = WsState {
        server,
        fallback_auth: auth,
    };
    let app = Router::new()
        .route("/lsp", get(websocket_upgrade))
        .with_state(state);
    let listener = TcpListener::bind(address).await?;
    axum::serve(listener, app).await
}

#[derive(Clone)]
struct WsState {
    server: LanguageServer,
    fallback_auth: AuthContext,
}

async fn websocket_upgrade(
    State(state): State<WsState>,
    headers: HeaderMap,
    socket: WebSocketUpgrade,
) -> Response {
    // Browsers cannot set arbitrary WebSocket headers; Web Studio must pass its
    // ephemeral access token in initialize.initializationOptions. Native clients
    // may instead use the standard Authorization header, which we never log.
    let header_auth = bearer_from_headers(&headers).map(|token| AuthContext {
        access_token: Some(token),
        ..state.fallback_auth.clone()
    });
    socket
        .max_frame_size(MAX_MESSAGE_BYTES)
        .on_upgrade(move |socket| {
            run_websocket(
                socket,
                state.server,
                header_auth.unwrap_or(state.fallback_auth),
            )
        })
}

async fn run_websocket(socket: WebSocket, server: LanguageServer, auth: AuthContext) {
    let session = server.session(auth);
    let (mut writer, mut reader) = socket.split();
    while let Some(Ok(message)) = reader.next().await {
        let bytes = match message {
            Message::Text(value) => value.as_bytes().to_vec(),
            Message::Binary(value) => value.to_vec(),
            Message::Ping(value) => {
                if writer.send(Message::Pong(value)).await.is_err() {
                    break;
                }
                continue;
            }
            Message::Close(_) => break,
            Message::Pong(_) => continue,
        };
        let output = match parse_request(&bytes) {
            Ok(request) => session.handle(request).await,
            Err(error) => vec![crate::protocol::error_response(None, error)],
        };
        for value in output {
            let Ok(encoded) = serde_json::to_string(&value) else {
                continue;
            };
            if writer.send(Message::Text(encoded.into())).await.is_err() {
                return;
            }
        }
    }
}

fn bearer_from_headers(headers: &HeaderMap) -> Option<String> {
    let raw = headers.get("authorization")?.to_str().ok()?;
    raw.strip_prefix("Bearer ")
        .or_else(|| raw.strip_prefix("bearer "))
        .map(str::trim)
        .filter(|token| !token.is_empty())
        .map(str::to_owned)
}

async fn run_framed<R, W>(reader: R, writer: W, session: LspSession) -> io::Result<()>
where
    R: AsyncRead + Unpin,
    W: AsyncWrite + Unpin,
{
    let mut reader = BufReader::new(reader);
    let mut writer = BufWriter::new(writer);
    while let Some(payload) = read_frame(&mut reader).await? {
        let output = match parse_request(&payload) {
            Ok(request) => session.handle(request).await,
            Err(error) => vec![crate::protocol::error_response(None, error)],
        };
        for value in output {
            let body = serde_json::to_vec(&value)
                .map_err(|error| io::Error::new(io::ErrorKind::InvalidData, error))?;
            write_frame(&mut writer, &body).await?;
        }
        writer.flush().await?;
    }
    Ok(())
}

async fn read_frame<R: AsyncRead + Unpin>(
    reader: &mut BufReader<R>,
) -> io::Result<Option<Vec<u8>>> {
    let mut content_length = None;
    let mut saw_header = false;
    loop {
        let mut line = Vec::new();
        let read = reader.read_until(b'\n', &mut line).await?;
        if read == 0 {
            return if saw_header {
                Err(io::Error::new(
                    io::ErrorKind::UnexpectedEof,
                    "LSP Content-Length header 不完整",
                ))
            } else {
                Ok(None)
            };
        }
        saw_header = true;
        let line = String::from_utf8(line)
            .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "LSP header 非 UTF-8"))?;
        let normalized = line.trim_end_matches(['\r', '\n']);
        if normalized.is_empty() {
            break;
        }
        let (key, value) = normalized
            .split_once(':')
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "LSP header 格式无效"))?;
        if key.eq_ignore_ascii_case("content-length") {
            let length = value.trim().parse::<usize>().map_err(|_| {
                io::Error::new(io::ErrorKind::InvalidData, "Content-Length 不是数字")
            })?;
            if length > MAX_MESSAGE_BYTES {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    "LSP 消息超过上限",
                ));
            }
            content_length = Some(length);
        }
    }
    let length = content_length
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "缺少 Content-Length"))?;
    let mut payload = vec![0; length];
    tokio::io::AsyncReadExt::read_exact(reader, &mut payload).await?;
    Ok(Some(payload))
}

async fn write_frame<W: AsyncWrite + Unpin>(
    writer: &mut BufWriter<W>,
    body: &[u8],
) -> io::Result<()> {
    writer
        .write_all(format!("Content-Length: {}\r\n\r\n", body.len()).as_bytes())
        .await?;
    writer.write_all(body).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn content_length_frame_round_trip() {
        let (client, server) = tokio::io::duplex(1024);
        let (mut client_read, mut client_write) = tokio::io::split(client);
        let server = async move {
            let mut reader = BufReader::new(server);
            let body = read_frame(&mut reader).await.unwrap().unwrap();
            assert_eq!(body, br#"{"jsonrpc":"2.0"}"#);
        };
        let client = async move {
            let payload = br#"{"jsonrpc":"2.0"}"#;
            client_write
                .write_all(format!("Content-Length: {}\r\n\r\n", payload.len()).as_bytes())
                .await
                .unwrap();
            client_write.write_all(payload).await.unwrap();
            let _ = &mut client_read;
        };
        tokio::join!(server, client);
    }
}
