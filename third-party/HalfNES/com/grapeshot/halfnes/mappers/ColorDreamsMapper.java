/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class ColorDreamsMapper
extends Mapper {
    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 1; n <= 32; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 1; n <= 8; ++n) {
            this.chr_map[8 - n] = this.chrsize - 1024 * n;
        }
    }

    @Override
    public final void cartWrite(int n, int n2) {
        int n3;
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        int n4 = n2 & 0xF;
        int n5 = n2 >> 4;
        for (n3 = 0; n3 < 8; ++n3) {
            this.chr_map[n3] = 1024 * (n3 + 8 * n5) & this.chrsize - 1;
        }
        for (n3 = 0; n3 < 32; ++n3) {
            this.prg_map[n3] = 1024 * (n3 + 32 * n4) & this.prgsize - 1;
        }
    }
}

