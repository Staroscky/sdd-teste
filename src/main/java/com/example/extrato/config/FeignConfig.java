package com.example.extrato.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.okhttp.LogbookInterceptor;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    @Bean
    public OkHttpClient okHttpClient(Logbook logbook) {
        return new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addNetworkInterceptor(new LogbookInterceptor(logbook))
                .build();
    }
}
