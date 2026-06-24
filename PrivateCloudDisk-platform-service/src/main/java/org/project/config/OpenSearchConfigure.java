package org.project.config;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class OpenSearchConfigure {

    @Value("${opensearch.host}")
    private String host;


    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient() {

        RestClientBuilder builder = RestClient.builder(org.apache.http.HttpHost.create(host))
                .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder
                );

        return new RestHighLevelClient(builder);
    }
}