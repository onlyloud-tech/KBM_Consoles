/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper36
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
        if (n < 33792 || n > 65534) {
            super.cartWrite(n, n2);
            return;
        }
        for (n3 = 0; n3 < 8; ++n3) {
            this.chr_map[n3] = 1024 * (n3 + 8 * n2) & this.chrsize - 1;
        }
        for (n3 = 0; n3 < 32; ++n3) {
            this.prg_map[n3] = 1024 * (n3 + 32 * (n2 >> 4)) & this.prgsize - 1;
        }
    }
}

