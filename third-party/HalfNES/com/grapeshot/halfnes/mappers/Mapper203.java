/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper203
extends Mapper {
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
    public int cartRead(int n) {
        return this.prg[this.prg_map[(n & 0x3FFF) >> 10] + (n & 0x3FFF & 0x3FF)];
    }

    @Override
    public final void cartWrite(int n, int n2) {
        int n3;
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        int n4 = n2 >> 2;
        int n5 = n2 & 3;
        for (n3 = 0; n3 < 8; ++n3) {
            this.chr_map[n3] = 1024 * (n3 + 8 * n5) & this.chrsize - 1;
        }
        for (n3 = 0; n3 < 16; ++n3) {
            this.prg_map[n3] = 1024 * (n3 + 16 * n4) & this.prgsize - 1;
        }
    }
}

