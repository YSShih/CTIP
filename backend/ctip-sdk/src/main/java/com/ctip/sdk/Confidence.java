package com.ctip.sdk;

/** 信心度:對「此 IOC 確為惡意」的信心,0–100(docs/spec/02-ddd-model.md §2.6)。 */
public record Confidence(int value) {

    public Confidence {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("confidence 必須在 0–100 之間:" + value);
        }
    }

    public static Confidence of(int value) {
        return new Confidence(value);
    }
}
