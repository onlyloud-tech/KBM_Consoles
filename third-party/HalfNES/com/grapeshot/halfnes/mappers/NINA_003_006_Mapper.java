/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class NINA_003_006_Mapper
extends Mapper {
    boolean m113 = true;

    public NINA_003_006_Mapper(int n) {
        switch (n) {
            case 79: {
                this.m113 = false;
                break;
            }
            case 113: {
                this.m113 = true;
            }
        }
    }

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
        for (int i = 16640; i < 24576; i += 512) {
            this.cartWrite(i, i + 255);
        }
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n < 16640 || n > 24575) {
            super.cartWrite(n, n2);
            return;
        }
        if (this.m113) {
            int n3;
            this.setmirroring((n2 & 0x80) != 0 ? Mapper.MirrorType.V_MIRROR : Mapper.MirrorType.H_MIRROR);
            for (n3 = 0; n3 < 8; ++n3) {
                this.chr_map[n3] = 1024 * (n3 + 8 * (n2 >> 3 & 8 | n2 & 7)) & this.chrsize - 1;
            }
            for (n3 = 0; n3 < 32; ++n3) {
                this.prg_map[n3] = 1024 * (n3 + 32 * (n2 >> 3 & 7)) & this.prgsize - 1;
            }
        } else {
            int n4;
            for (n4 = 0; n4 < 8; ++n4) {
                this.chr_map[n4] = 1024 * (n4 + 8 * n2) & this.chrsize - 1;
            }
            for (n4 = 0; n4 < 32; ++n4) {
                this.prg_map[n4] = 1024 * (n4 + 32 * (n2 >> 3)) & this.prgsize - 1;
            }
        }
    }
}

