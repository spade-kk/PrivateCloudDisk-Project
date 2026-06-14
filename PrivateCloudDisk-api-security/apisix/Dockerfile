FROM apache/apisix:latest

USER root

ARG CORAZA_VERSION=0.5.0
ARG CORAZA_ZIP=coraza-proxy-wasm-${CORAZA_VERSION}.zip

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget unzip ca-certificates \
    && mkdir -p /usr/local/apisix/proxywasm \
    && wget -O /tmp/${CORAZA_ZIP} https://github.com/corazawaf/coraza-proxy-wasm/releases/download/${CORAZA_VERSION}/${CORAZA_ZIP} \
    && unzip /tmp/${CORAZA_ZIP} -d /usr/local/apisix/proxywasm \
    && rm -f /tmp/${CORAZA_ZIP} \
    && chown -R apisix:apisix /usr/local/apisix/proxywasm \
    && apt-get purge -y wget unzip \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

USER apisix