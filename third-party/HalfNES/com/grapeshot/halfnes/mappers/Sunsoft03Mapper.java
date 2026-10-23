/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Sunsoft03Mapper
extends Mapper {
    int[] chrbank = new int[]{0, 0, 0, 0};
    private int irqctr = 0;
    private boolean irqenable = false;
    private boolean interrupted = false;
    private boolean irqtoggle = false;

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
        if (n >= 34816 && n <= 36863) {
            this.chrbank[0] = n2;
            this.setupchr();
        } else if (n >= 38912 && n <= 40959) {
            this.chrbank[1] = n2;
            this.setupchr();
        } else if (n >= 43008 && n <= 45055) {
            this.chrbank[2] = n2;
            this.setupchr();
        } else if (n >= 47104 && n <= 49151) {
            this.chrbank[3] = n2;
            this.setupchr();
        } else if (n >= 51200 && n <= 53247) {
            if (!this.irqtoggle) {
                this.irqctr = this.irqctr & 0xFF | n2 << 8;
                this.irqtoggle = true;
            } else {
                this.irqctr = this.irqctr & 0xFF00 | n2 & 0xFF;
                this.irqtoggle = false;
            }
        } else if (n >= 55296 && n <= 57343) {
            if (this.interrupted) {
                --this.cpu.interrupt;
                this.interrupted = false;
            }
            this.irqenable = (n2 & 0x10) != 0;
            this.irqtoggle = false;
        } else if (n >= 59392 && n <= 61439) {
            switch (n2 & 3) {
                case 0: {
                    this.setmirroring(Mapper.MirrorType.V_MIRROR);
                    break;
                }
                case 1: {
                    this.setmirroring(Mapper.MirrorType.H_MIRROR);
                    break;
                }
                case 2: {
                    this.setmirroring(Mapper.MirrorType.SS_MIRROR0);
                    break;
                }
                case 3: {
                    this.setmirroring(Mapper.MirrorType.SS_MIRROR1);
                }
            }
        } else if (n >= 63488 && n <= 65535) {
            for (int i = 0; i < 16; ++i) {
                this.prg_map[i] = 1024 * (i + 16 * n2) & this.prgsize - 1;
            }
        }
    }

    @Override
    public void cpucycle(int n) {
        if (this.irqenable) {
            if (this.irqctr <= 0) {
                this.irqctr = 65535;
                if (!this.interrupted) {
                    ++this.cpu.interrupt;
                    this.interrupted = true;
                }
                this.irqenable = false;
            } else {
                this.irqctr -= n;
            }
        }
    }

    private void setupchr() {
        this.setppubank(2, 0, this.chrbank[0]);
        this.setppubank(2, 2, this.chrbank[1]);
        this.setppubank(2, 4, this.chrbank[2]);
        this.setppubank(2, 6, this.chrbank[3]);
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (i + 2 * n3) % this.chrsize;
        }
    }
}

