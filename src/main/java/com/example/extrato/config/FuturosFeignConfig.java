package com.example.extrato.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

public class FuturosFeignConfig {

    @Bean
    public feign.Client futurosFeignClient(@Qualifier("futurosOkHttpClient") OkHttpClient okHttpClient) {
        return new feign.okhttp.OkHttpClient(okHttpClient);
    }
}
