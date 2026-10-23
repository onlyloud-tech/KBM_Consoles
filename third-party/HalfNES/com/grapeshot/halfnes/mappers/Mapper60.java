/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper60
extends Mapper {
    int reg = 0;

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * n & this.chrsize - 1;
        }
        for (n = 0; n < 16; ++n) {
            this.prg_map[n] = 1024 * n & this.prgsize - 1;
        }
        for (n = 0; n < 16; ++n) {
            this.prg_map[n + 16] = 1024 * n & this.prgsize - 1;
        }
    }

    @Override
    public void reset() {
        int n;
        this.reg = this.reg + 1 & 3;
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * (n + 8 * this.reg) & this.chrsize - 1;
        }
        for (n = 0; n < 16; ++n) {
            this.prg_map[n] = 1024 * (n + 16 * this.reg) & this.prgsize - 1;
        }
        for (n = 0; n < 16; ++n) {
            this.prg_map[n + 16] = 1024 * (n + 16 * this.reg) & this.prgsize - 1;
        }
    }
}

