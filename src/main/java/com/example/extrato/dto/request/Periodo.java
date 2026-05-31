package com.example.extrato.dto.request;

public enum Periodo {
    SETE_DIAS("7_DIAS"),
    QUINZE_DIAS("15_DIAS"),
    TRINTA_DIAS("30_DIAS"),
    SESSENTA_DIAS("60_DIAS"),
    PERSONALIZADO("PERSONALIZADO");

    public final String id;

    Periodo(String id) {
        this.id = id;
    }

    public static Periodo fromId(String id) {
        for (Periodo p : values()) {
            if (p.id.equals(id)) return p;
        }
        throw new IllegalArgumentException("Periodo invalido: " + id);
    }
}
