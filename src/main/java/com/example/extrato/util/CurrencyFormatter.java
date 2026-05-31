package com.example.extrato.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Component
public class CurrencyFormatter {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private static final ThreadLocal<NumberFormat> FORMAT =
            ThreadLocal.withInitial(() -> NumberFormat.getCurrencyInstance(PT_BR));

    public String format(BigDecimal valor) {
        return FORMAT.get().format(valor).replace(' ', ' ');
    }
}
