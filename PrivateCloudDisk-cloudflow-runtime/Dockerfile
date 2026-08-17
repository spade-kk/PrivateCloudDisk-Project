FROM rust:1.80-bookworm AS builder
WORKDIR /src
COPY Cargo.toml Cargo.lock* ./
COPY src ./src
RUN cargo build --release --locked || cargo build --release

FROM gcr.io/distroless/cc-debian12:nonroot
COPY --from=builder /src/target/release/cloudflow-runtime /usr/local/bin/cloudflow-runtime
COPY --from=builder /src/target/release/cloudflowc /usr/local/bin/cloudflowc
USER nonroot:nonroot
ENTRYPOINT ["/usr/local/bin/cloudflow-runtime"]
