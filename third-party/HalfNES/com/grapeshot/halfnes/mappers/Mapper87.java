/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper87
extends Mapper {
    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 32; ++n) {
            this.prg_map[n] = 1024 * n & this.prgsize - 1;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * n & this.chrsize - 1;
        }
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n >= 24576 && n < 32768) {
            int n3 = n2 >> 1 & 1;
            int n4 = n2 & 1;
            for (int i = 0; i < 8; ++i) {
                this.chr_map[i] = 1024 * (i + 8 * ((n4 << 1) + n3)) & this.chrsize - 1;
            }
        } else {
            super.cartWrite(n, n2);
        }
    }
}

