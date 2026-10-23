/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper152
extends Mapper {
    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 16; ++n) {
            this.prg_map[n] = 1024 * n & this.prgsize - 1;
        }
        for (n = 1; n <= 16; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
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
        byte by = (byte)(n2 >> 4 & 0xF);
        int n4 = n2 & 0xF;
        for (n3 = 0; n3 < 8; ++n3) {
            this.chr_map[n3] = 1024 * (n3 + 8 * n4) & this.chrsize - 1;
        }
        for (n3 = 0; n3 < 16; ++n3) {
            this.prg_map[n3] = 1024 * (n3 + 16 * by) & this.prgsize - 1;
        }
        this.setmirroring((n2 & 0x80) != 0 ? Mapper.MirrorType.SS_MIRROR1 : Mapper.MirrorType.SS_MIRROR0);
    }
}

