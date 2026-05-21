package com.leafy.searchservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

import java.time.Duration;
import java.util.Arrays;

@Configuration
public class ElasticSearchConfig extends ElasticsearchConfiguration {

    @Value("${elasticsearch.url}")
    private String url;

    @Value("${elasticsearch.username}")
    private String username;

    @Value("${elasticsearch.password}")
    private String password;

    @Value("${elasticsearch.ssl:false}")
    private boolean ssl;

    @Override
    public ClientConfiguration clientConfiguration() {
        String[] hosts = Arrays.stream(url.split(","))
                .map(s -> s.replace("http://", "").replace("https://", ""))
                .map(String::trim)
                .toArray(String[]::new);

        var builder = ClientConfiguration.builder()
                .connectedTo(hosts);

        if (ssl) {
            builder.usingSsl();
        }

        return builder
                .withBasicAuth(username, password)
                .withConnectTimeout(Duration.ofSeconds(5))
                .withSocketTimeout(Duration.ofSeconds(30))
                .build();
    }
}
