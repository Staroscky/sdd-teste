package com.example.extrato.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

public class RecentesFeignConfig {

    @Bean
    public feign.Client recentesFeignClient(@Qualifier("recentesOkHttpClient") OkHttpClient okHttpClient) {
        return new feign.okhttp.OkHttpClient(okHttpClient);
    }
}
