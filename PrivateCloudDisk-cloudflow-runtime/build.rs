fn main() -> Result<(), Box<dyn std::error::Error>> {
    // [CLOUDFLOW-RUNTIME-AGENT-001] 使用 vendored protoc 保证 CI/容器无需依赖宿主机安装。
    let protoc = protoc_bin_vendored::protoc_bin_path()?;
    std::env::set_var("PROTOC", protoc);
    tonic_build::configure()
        .build_server(true)
        .build_client(true)
        .compile_protos(&["proto/cloudflow_runtime.proto"], &["proto"])?;
    println!("cargo:rerun-if-changed=proto/cloudflow_runtime.proto");
    Ok(())
}
