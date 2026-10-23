/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper92
extends Mapper {
    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 16; ++n) {
            this.prg_map[16 + n] = 1024 * n & this.prgsize - 1;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * n & this.chrsize - 1;
        }
    }

    @Override
    public final void cartWrite(int n, int n2) {
        int n3;
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        if ((n2 & 0x40) != 0) {
            for (n3 = 0; n3 < 8; ++n3) {
                this.chr_map[n3] = 1024 * (n3 + 8 * (n2 & 0xF)) & this.chrsize - 1;
            }
        }
        if ((n2 & 0x80) != 0) {
            for (n3 = 0; n3 < 16; ++n3) {
                this.prg_map[16 + n3] = 1024 * (n3 + 16 * (n2 & 0xF)) & this.prgsize - 1;
            }
        }
    }
}

