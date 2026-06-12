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

    @Value("${opensearch.username}")
    private String username;

    @Value("${opensearch.password}")
    private String password;

    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient() {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(username, password)
        );

        RestClientBuilder builder = RestClient.builder(org.apache.http.HttpHost.create(host))
                .setHttpClientConfigCallback(httpClientBuilder ->
                        {
                            try {
                                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                                                .setSSLContext(SSLContextBuilder.create()
                                                    .loadTrustMaterial((chain, authType) -> true) // 信任所有
                                                    .build())
                                                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                            } catch (NoSuchAlgorithmException e) {
                                throw new RuntimeException(e);
                            } catch (KeyManagementException e) {
                                throw new RuntimeException(e);
                            } catch (KeyStoreException e) {
                                throw new RuntimeException(e);
                            }
                        } // 跳过主机名验证
                );

        return new RestHighLevelClient(builder);
    }
}