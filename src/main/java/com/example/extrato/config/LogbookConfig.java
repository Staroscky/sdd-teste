package com.example.extrato.config;

import org.springframework.context.annotation.Configuration;

// Logbook inbound (HTTP requests to this BFF) is auto-configured by logbook-spring-boot-starter.
// Logbook outbound (Feign calls to upstream APIs) is configured via LogbookInterceptor
// added directly to the OkHttpClient bean in FeignConfig.
@Configuration
public class LogbookConfig {
}
