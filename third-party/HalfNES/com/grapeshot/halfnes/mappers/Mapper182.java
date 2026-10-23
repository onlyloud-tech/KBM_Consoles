/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper182
extends Mapper {
    private int whichbank = 0;
    private boolean prgconfig = false;
    private boolean chrconfig = false;
    private int irqctrreload = 0;
    private int irqctr = 0;
    private boolean irqenable = false;
    private boolean irqreload = false;
    private int bank6 = 0;
    private int[] chrreg = new int[]{0, 0, 0, 0, 0, 0};
    private boolean interrupted = false;

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
        this.setbank6();
    }

    @Override
    public final void cartWrite(int n, int n2) {
        block21: {
            block23: {
                block22: {
                    block20: {
                        if (n < 32768 || n > 65535) {
                            super.cartWrite(n, n2);
                            return;
                        }
                        if ((n & 1) == 0) break block20;
                        if (n >= 32768 && n <= 40959) {
                            this.setmirroring((n2 & 1) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
                        } else if (n < 40960 || n > 49151) {
                            if (n >= 49152 && n <= 57343) {
                                this.irqreload = true;
                                this.irqctrreload = n2;
                            } else if (n >= 57344 && n <= 65535) {
                                this.irqenable = true;
                            }
                        }
                        break block21;
                    }
                    if (n < 40960 || n > 49151) break block22;
                    this.whichbank = n2 & 7;
                    this.prgconfig = (n2 & 0x10) != 0;
                    this.chrconfig = (n2 & 0x20) != 0;
                    this.setupchr();
                    this.setbank6();
                    break block21;
                }
                if (n < 49152 || n > 57343) break block23;
                switch (this.whichbank) {
                    case 0: {
                        this.chrreg[0] = n2;
                        this.setupchr();
                        break;
                    }
                    case 1: {
                        this.chrreg[3] = n2;
                        this.setupchr();
                        break;
                    }
                    case 2: {
                        this.chrreg[1] = n2;
                        this.setupchr();
                        break;
                    }
                    case 3: {
                        this.chrreg[5] = n2;
                        this.setupchr();
                        break;
                    }
                    case 4: {
                        this.bank6 = n2;
                        this.setbank6();
                        break;
                    }
                    case 5: {
                        for (int i = 0; i < 8; ++i) {
                            this.prg_map[i + 8] = 1024 * (i + n2 * 8) % this.prgsize;
                        }
                        break block21;
                    }
                    case 6: {
                        this.chrreg[2] = n2;
                        this.setupchr();
                        break;
                    }
                    case 7: {
                        this.chrreg[4] = n2;
                        this.setupchr();
                    }
                }
                break block21;
            }
            if (n >= 57344 && n <= 65535) {
                if (this.interrupted) {
                    --this.cpu.interrupt;
                }
                this.interrupted = false;
                this.irqenable = false;
                this.irqctr = this.irqctrreload;
            }
        }
    }

    private void setupchr() {
        if (this.chrconfig) {
            this.setppubank(1, 0, this.chrreg[2]);
            this.setppubank(1, 1, this.chrreg[3]);
            this.setppubank(1, 2, this.chrreg[4]);
            this.setppubank(1, 3, this.chrreg[5]);
            this.setppubank(2, 4, this.chrreg[0] >> 1 << 1);
            this.setppubank(2, 6, this.chrreg[1] >> 1 << 1);
        } else {
            this.setppubank(1, 4, this.chrreg[2]);
            this.setppubank(1, 5, this.chrreg[3]);
            this.setppubank(1, 6, this.chrreg[4]);
            this.setppubank(1, 7, this.chrreg[5]);
            this.setppubank(2, 0, this.chrreg[0] >> 1 << 1);
            this.setppubank(2, 2, this.chrreg[1] >> 1 << 1);
        }
    }

    private void setbank6() {
        if (!this.prgconfig) {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i] = 1024 * (i + this.bank6 * 8) % this.prgsize;
                this.prg_map[i + 16] = this.prgsize - 16384 + 1024 * i;
            }
        } else {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i] = this.prgsize - 16384 + 1024 * i;
                this.prg_map[i + 16] = 1024 * (i + this.bank6 * 8) % this.prgsize;
            }
        }
    }

    @Override
    public void notifyscanline(int n) {
        if (n > 239 && n != 261) {
            return;
        }
        if (!this.ppu.mmc3CounterClocking()) {
            return;
        }
        if (this.irqreload) {
            this.irqreload = false;
            this.irqctr = this.irqctrreload;
        }
        if (this.irqctr-- <= 0) {
            if (this.irqctrreload == 0) {
                return;
            }
            if (this.irqenable && !this.interrupted) {
                ++this.cpu.interrupt;
                this.interrupted = true;
            }
            this.irqctr = this.irqctrreload;
        }
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) % this.chrsize;
        }
    }
}

