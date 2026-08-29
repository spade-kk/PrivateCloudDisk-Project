# CloudFlow MCP Server is intentionally a small, stateless HTTP adapter.  The
# final image has no package manager, shell, database client or cloud SDK, so it
# cannot be repurposed to bypass Capability Hub's permission boundary.
FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . ./
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w' -o /out/cloudflow-mcp-server ./cmd/mcp-server

FROM gcr.io/distroless/static-debian12:nonroot
WORKDIR /app
COPY --from=build /out/cloudflow-mcp-server /usr/local/bin/cloudflow-mcp-server
USER nonroot:nonroot
EXPOSE 8093
ENTRYPOINT ["/usr/local/bin/cloudflow-mcp-server"]
