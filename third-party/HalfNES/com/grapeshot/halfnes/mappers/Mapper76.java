/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper76
extends Mapper {
    private int whichbank = 0;
    private int[] chrreg = new int[]{0, 0, 0, 0, 0, 0};

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 1; n <= 32; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 0;
        }
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        if (n == 32769) {
            if (this.whichbank <= 5) {
                this.chrreg[this.whichbank] = n2;
                this.setupchr();
            } else if (this.whichbank == 6) {
                for (int i = 0; i < 8; ++i) {
                    this.prg_map[i] = 1024 * (i + n2 * 8) % this.prgsize;
                }
            } else if (this.whichbank == 7) {
                for (int i = 0; i < 8; ++i) {
                    this.prg_map[i + 8] = 1024 * (i + n2 * 8) % this.prgsize;
                }
            }
        } else if (n == 32768) {
            this.whichbank = n2 & 7;
        }
    }

    private void setupchr() {
        this.setppubank(2, 0, this.chrreg[2] << 1);
        this.setppubank(2, 2, this.chrreg[3] << 1);
        this.setppubank(2, 4, this.chrreg[4] << 1);
        this.setppubank(2, 6, this.chrreg[5] << 1);
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) & this.chrsize - 1;
        }
    }
}

