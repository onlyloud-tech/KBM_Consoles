/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class VRC1Mapper
extends Mapper {
    int prgbank0;
    int prgbank1;
    int prgbank2 = 0;
    int[] chrbank = new int[]{0, 0};

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 24; ++n) {
            this.prg_map[n] = 1024 * n & this.prgsize - 1;
        }
        for (n = 1; n <= 8; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
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
        switch (n >> 12) {
            case 8: {
                this.prgbank0 = n2 & 0xF;
                this.setbanks();
                break;
            }
            case 9: {
                this.setmirroring((n2 & 1) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
                this.chrbank[0] = this.chrbank[0] & 0xF | n2 << 3 & 0x10;
                this.chrbank[1] = this.chrbank[1] & 0xF | n2 << 2 & 0x10;
                this.setbanks();
                break;
            }
            case 10: {
                this.prgbank1 = n2 & 0xF;
                this.setbanks();
                break;
            }
            case 12: {
                this.prgbank2 = n2 & 0xF;
                this.setbanks();
                break;
            }
            case 14: {
                this.chrbank[0] = this.chrbank[0] & 0x10 | n2 & 0xF;
                this.setbanks();
                break;
            }
            case 15: {
                this.chrbank[1] = this.chrbank[1] & 0x10 | n2 & 0xF;
                this.setbanks();
            }
        }
    }

    private void setbanks() {
        int n;
        for (n = 1; n <= 8; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.prg_map[n] = 1024 * (n + 8 * this.prgbank0) % (this.prgsize - 1);
        }
        for (n = 0; n < 8; ++n) {
            this.prg_map[n + 8] = 1024 * (n + 8 * this.prgbank1) % (this.prgsize - 1);
        }
        for (n = 0; n < 8; ++n) {
            this.prg_map[n + 16] = 1024 * (n + 8 * this.prgbank2) % (this.prgsize - 1);
        }
        this.setppubank(4, 0, this.chrbank[0]);
        this.setppubank(4, 4, this.chrbank[1]);
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (i + 4 * n3) % (this.chrsize - 1);
        }
    }
}

