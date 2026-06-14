FROM opensearchproject/opensearch:3.2.0

# 安装 analysis-ik 中文分词插件
RUN ./bin/opensearch-plugin install --batch \
    https://github.com/aparo/opensearch-analysis-ik/releases/download/3.2.0/opensearch-analysis-ik.zip \
    && ln -s /usr/share/opensearch/config/opensearch-analysis-ik \
             /usr/share/opensearch/plugins/opensearch-analysis-ik/config