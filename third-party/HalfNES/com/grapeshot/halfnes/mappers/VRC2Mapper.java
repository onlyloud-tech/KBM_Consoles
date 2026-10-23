/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class VRC2Mapper
extends Mapper {
    int prgbank0;
    int prgbank1 = 0;
    int[] chrbank = new int[]{0, 0, 0, 0, 0, 0, 0, 0};

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 1; n <= 32; ++n) {
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
        boolean bl = (n & 2) != 0;
        boolean bl2 = (n & 1) != 0;
        switch (n >> 12) {
            case 8: {
                this.prgbank0 = n2 & 0xF;
                break;
            }
            case 9: {
                switch (n2 & 1) {
                    case 0: {
                        this.setmirroring(Mapper.MirrorType.V_MIRROR);
                        break;
                    }
                    case 1: {
                        this.setmirroring(Mapper.MirrorType.H_MIRROR);
                    }
                }
                break;
            }
            case 10: {
                this.prgbank1 = n2 & 0xF;
                break;
            }
            case 11: 
            case 12: 
            case 13: 
            case 14: {
                n2 &= 0xF;
                int n3 = (n - 45056 >> 11) + (bl2 ? 1 : 0);
                int n4 = this.chrbank[n3];
                if (!bl) {
                    n4 &= 0xF0;
                    n4 |= n2;
                } else {
                    n4 &= 0xF;
                    n4 |= n2 << 4;
                }
                this.chrbank[n3] = n4;
            }
        }
        if (n < 61440) {
            this.setbanks();
        }
    }

    private void setbanks() {
        int n;
        for (n = 1; n <= 16; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.prg_map[n] = 1024 * (n + 8 * this.prgbank0) % this.prgsize;
        }
        for (n = 0; n < 8; ++n) {
            this.prg_map[n + 8] = 1024 * (n + 8 * this.prgbank1) % this.prgsize;
        }
        for (n = 0; n < 8; ++n) {
            this.setppubank(1, n, this.chrbank[n] >> 1);
        }
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) % this.chrsize;
        }
    }
}

