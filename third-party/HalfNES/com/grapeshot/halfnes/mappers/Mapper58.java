/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper58
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
    public final void reset() {
        this.cartWrite(32768, this.cartRead(32768));
    }

    @Override
    public final void cartWrite(int n, int n2) {
        int n3;
        int n4;
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        this.setmirroring((n & 0x80) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
        for (n4 = 0; n4 < 8; ++n4) {
            this.chr_map[n4] = 1024 * (n4 + 8 * (n >> 3)) & this.chrsize - 1;
        }
        n4 = n & ~(~n >> 6 & 1);
        for (n3 = 0; n3 < 16; ++n3) {
            this.prg_map[n3] = 1024 * (n3 + 16 * n4) & this.prgsize - 1;
        }
        n4 = n | ~n >> 6 & 1;
        for (n3 = 0; n3 < 16; ++n3) {
            this.prg_map[16 + n3] = 1024 * (n3 + 16 * n4) & this.prgsize - 1;
        }
    }
}

