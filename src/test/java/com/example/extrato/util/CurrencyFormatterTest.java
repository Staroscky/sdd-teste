package com.example.extrato.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyFormatterTest {

    private final CurrencyFormatter formatter = new CurrencyFormatter();

    @Test
    void formatValuePositivo() {
        assertThat(formatter.format(new BigDecimal("1250.00"))).contains("1.250,00");
    }

    @Test
    void formatValueZero() {
        assertThat(formatter.format(BigDecimal.ZERO)).contains("0,00");
    }

    @Test
    void formatValueNegativo() {
        String resultado = formatter.format(new BigDecimal("-99.90"));
        assertThat(resultado).contains("99,90");
    }

    @Test
    void formatThreadSafe() throws Exception {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> formatter.format(new BigDecimal("1234.56"))));
        }

        executor.shutdown();
        for (Future<String> f : futures) {
            assertThat(f.get()).contains("1.234,56");
        }
    }
}
