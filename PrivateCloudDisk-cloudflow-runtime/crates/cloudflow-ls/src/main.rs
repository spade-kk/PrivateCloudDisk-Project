//! cloudflow-ls command line entry point.

use clap::Parser;
use cloudflow_ls::{
    auth::{load_local_token, AuthContext},
    capability::{CapabilityProvider, DEFAULT_CAPABILITY_API},
    server::LanguageServer,
    transport,
};
use std::{net::SocketAddr, path::PathBuf, process::ExitCode, time::Duration};

#[derive(Debug, Parser)]
#[command(
    name = "cloudflow-ls",
    version,
    about = "CloudFlow Language Server (LSP)"
)]
struct Cli {
    /// Standard LSP Content-Length framing on stdin/stdout (the default and VS Code mode).
    #[arg(long)]
    stdio: bool,
    /// Serve Content-Length-framed LSP over TCP.
    #[arg(long, value_name = "ADDRESS")]
    tcp: Option<SocketAddr>,
    /// Serve Content-Length-framed LSP over a Unix Domain Socket (macOS/Linux).
    #[arg(long, value_name = "PATH")]
    unix_socket: Option<PathBuf>,
    /// Serve LSP JSON-RPC text/binary frames at ws://ADDRESS/lsp for Web Studio.
    #[arg(long, value_name = "ADDRESS")]
    websocket: Option<SocketAddr>,
    /// Capability Hub gateway base URL, normally http://gateway:8080/api/v1.
    #[arg(long, env = "CLOUDFLOW_CAPABILITY_API", default_value = DEFAULT_CAPABILITY_API)]
    capability_api: String,
    /// Local token file (default: ~/.cloudflow/token); must be owner-readable only on Unix.
    #[arg(long, env = "CLOUDFLOW_TOKEN_FILE")]
    token_file: Option<PathBuf>,
    #[arg(long, env = "CLOUDFLOW_TENANT_ID")]
    tenant_id: Option<String>,
    #[arg(long, env = "CLOUDFLOW_SPACE_ID")]
    space_id: Option<String>,
    #[arg(long, default_value_t = 300)]
    capability_cache_ttl_secs: u64,
}

#[tokio::main]
async fn main() -> ExitCode {
    let cli = Cli::parse();
    let modes = [
        cli.stdio,
        cli.tcp.is_some(),
        cli.unix_socket.is_some(),
        cli.websocket.is_some(),
    ]
    .into_iter()
    .filter(|enabled| *enabled)
    .count();
    if modes > 1 {
        eprintln!(
            "cloudflow-ls: 只能选择一种传输模式（--stdio / --tcp / --unix-socket / --websocket）"
        );
        return ExitCode::from(64);
    }
    let token = match load_local_token(cli.token_file.as_deref()) {
        Ok(value) => value,
        Err(error) => {
            eprintln!("cloudflow-ls: {error}");
            return ExitCode::from(78);
        }
    };
    let auth = AuthContext {
        access_token: token,
        tenant_id: cli.tenant_id,
        space_id: cli.space_id,
    };
    let provider = match CapabilityProvider::new(
        cli.capability_api,
        Duration::from_secs(cli.capability_cache_ttl_secs.max(1)),
    ) {
        Ok(value) => value,
        Err(error) => {
            eprintln!("cloudflow-ls: 无法初始化 Capability Provider：{error}");
            return ExitCode::from(78);
        }
    };
    let server = LanguageServer::new(provider);
    let result = if let Some(address) = cli.tcp {
        transport::serve_tcp(server, address, auth).await
    } else if let Some(path) = cli.unix_socket {
        transport::serve_unix(server, &path, auth).await
    } else if let Some(address) = cli.websocket {
        transport::serve_websocket(server, address, auth).await
    } else {
        transport::serve_stdio(server, auth).await
    };
    match result {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("cloudflow-ls 已停止：{error}");
            ExitCode::from(1)
        }
    }
}
