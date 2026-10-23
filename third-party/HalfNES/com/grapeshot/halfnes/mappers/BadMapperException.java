/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

public class BadMapperException
extends Exception {
    public String e;

    public BadMapperException(String string) {
        this.e = string;
    }

    @Override
    public String getMessage() {
        return this.e;
    }
}

