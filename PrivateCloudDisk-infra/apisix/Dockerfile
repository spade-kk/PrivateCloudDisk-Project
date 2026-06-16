FROM apache/apisix:latest

USER root

# ========== 安装 Coraza WAF 插件 ==========
ARG CORAZA_VERSION=0.5.0
ARG CORAZA_ZIP=coraza-proxy-wasm-${CORAZA_VERSION}.zip

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget unzip ca-certificates git \
    && mkdir -p /usr/local/apisix/proxywasm \
    && wget -O /tmp/${CORAZA_ZIP} https://github.com/corazawaf/coraza-proxy-wasm/releases/download/${CORAZA_VERSION}/${CORAZA_ZIP} \
    && unzip /tmp/${CORAZA_ZIP} -d /usr/local/apisix/proxywasm \
    && rm -f /tmp/${CORAZA_ZIP} \
    # ========== 下载 CRS 规则集 ==========
    && git clone --depth 1 --branch v3.3.4 https://github.com/coreruleset/coreruleset /usr/local/apisix/coreruleset \
    && cp /usr/local/apisix/coreruleset/crs-setup.conf.example /usr/local/apisix/coreruleset/crs-setup.conf \
    # ========== 清理 ==========
    && apt-get purge -y wget unzip git \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/* \
    && chown -R apisix:apisix /usr/local/apisix/proxywasm /usr/local/apisix/coreruleset

# 复制 APISIX 配置
COPY config.yaml /usr/local/apisix/conf/config.yaml
COPY apisix.yaml /usr/local/apisix/conf/apisix.yaml

USER apisix