FROM opensearchproject/opensearch:2.10.0

# 安装 analysis-ik 中文分词插件
RUN ./bin/opensearch-plugin install --batch \
    https://github.com/aparo/opensearch-analysis-ik/releases/download/2.10.0/opensearch-analysis-ik-2.10.0.zip