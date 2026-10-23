/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class MMC4Mapper
extends Mapper {
    boolean chrlatchL = true;
    boolean chrlatchR = false;
    int chrbankL1 = 0;
    int chrbankR1 = 0;
    int chrbankL2 = 0;
    int chrbankR2 = 0;

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
        if (n >= 32768 && n <= 45055) {
            for (int i = 0; i < 16; ++i) {
                this.prg_map[i] = 1024 * (i + 16 * n2) & this.prgsize - 1;
            }
        } else if (n >= 45056 && n <= 49151) {
            this.chrbankL1 = n2 & 0x1F;
            this.setupPPUBanks();
        } else if (n >= 49152 && n <= 53247) {
            this.chrbankL2 = n2 & 0x1F;
            this.setupPPUBanks();
        } else if (n >= 53248 && n <= 57343) {
            this.chrbankR1 = n2 & 0x1F;
            this.setupPPUBanks();
        } else if (n >= 57344 && n <= 61439) {
            this.chrbankR2 = n2 & 0x1F;
            this.setupPPUBanks();
        } else if (n >= 61440 && n <= 65535) {
            this.setmirroring((n2 & 1) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
        }
    }

    @Override
    public int ppuRead(int n) {
        int n2 = super.ppuRead(n);
        if ((n & 8) != 0) {
            switch (n >> 4) {
                case 253: {
                    this.chrlatchL = false;
                    this.setupPPUBanks();
                    break;
                }
                case 254: {
                    this.chrlatchL = true;
                    this.setupPPUBanks();
                    break;
                }
                case 509: {
                    this.chrlatchR = false;
                    this.setupPPUBanks();
                    break;
                }
                case 510: {
                    this.chrlatchR = true;
                    this.setupPPUBanks();
                }
            }
        }
        return n2;
    }

    private void setupPPUBanks() {
        if (this.chrlatchL) {
            this.setppubank(4, 0, this.chrbankL2);
        } else {
            this.setppubank(4, 0, this.chrbankL1);
        }
        if (this.chrlatchR) {
            this.setppubank(4, 4, this.chrbankR2);
        } else {
            this.setppubank(4, 4, this.chrbankR1);
        }
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n * n3 + i) % this.chrsize;
        }
    }
}

