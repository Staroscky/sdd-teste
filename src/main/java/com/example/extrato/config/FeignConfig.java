package com.example.extrato.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.okhttp.LogbookInterceptor;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(UpstreamProperties.class)
public class FeignConfig {

    @Bean
    @Qualifier("recentesOkHttpClient")
    public OkHttpClient recentesOkHttpClient(Logbook logbook, UpstreamProperties properties) {
        UpstreamProperties.TimeoutConfig timeout = properties.recentes().timeout();
        return new OkHttpClient.Builder()
                .connectTimeout(timeout.connect(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.read(), TimeUnit.MILLISECONDS)
                .addNetworkInterceptor(new LogbookInterceptor(logbook))
                .build();
    }

    @Bean
    @Qualifier("futurosOkHttpClient")
    public OkHttpClient futurosOkHttpClient(Logbook logbook, UpstreamProperties properties) {
        UpstreamProperties.TimeoutConfig timeout = properties.futuros().timeout();
        return new OkHttpClient.Builder()
                .connectTimeout(timeout.connect(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.read(), TimeUnit.MILLISECONDS)
                .addNetworkInterceptor(new LogbookInterceptor(logbook))
                .build();
    }
}
