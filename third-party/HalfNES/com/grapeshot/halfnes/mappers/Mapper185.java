/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper185
extends Mapper {
    boolean chr_enabled = true;

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 16; ++n) {
            this.prg_map[n] = 1024 * n & this.prgsize - 1;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * n & this.chrsize - 1;
        }
    }

    @Override
    public int ppuRead(int n) {
        if (!this.chr_enabled) {
            this.chr_enabled = true;
            return 18;
        }
        if (n < 8192) {
            return this.chr[this.chr_map[n >> 10] + (n & 0x3FF)];
        }
        return super.ppuRead(n);
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        for (int i = 0; i < 8; ++i) {
            this.chr_map[i] = 1024 * (i + 8 * (n2 & 3)) & this.chrsize - 1;
            this.chr_enabled = (this.chr_map[i] & 0xF) > 0 && this.chr_map[i] != 19;
        }
    }
}

