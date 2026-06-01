package com.example.extrato.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "upstream")
public record UpstreamProperties(
        UpstreamConfig recentes,
        UpstreamConfig futuros
) {

    public record UpstreamConfig(
            String url,
            TimeoutConfig timeout
    ) {}

    public record TimeoutConfig(
            int connect,
            int read
    ) {}
}
