/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class AnromMapper
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
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        for (int i = 0; i < 32; ++i) {
            this.prg_map[i] = 1024 * (i + 32 * (n2 & 0xF)) & this.prgsize - 1;
        }
        this.setmirroring((n2 & 0x10) != 0 ? Mapper.MirrorType.SS_MIRROR1 : Mapper.MirrorType.SS_MIRROR0);
    }
}

