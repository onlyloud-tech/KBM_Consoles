/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class TengenRamboMapper
extends Mapper {
    private int whichbank = 0;
    private boolean prgconfig = false;
    private boolean chrconfig = false;
    private boolean chrmode1k = false;
    private boolean irqmode = false;
    private int irqctrreload = 0;
    private int irqctr = 0;
    private boolean irqenable = false;
    private boolean irqreload = false;
    private int prgreg0 = 0;
    private int prgreg1 = 0;
    private int prgreg2 = 0;
    private int[] chrreg = new int[8];
    private boolean interrupted = false;
    int remainder;
    boolean intnextcycle = false;

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 8; ++n) {
            this.prg_map[n] = 1024 * n;
            this.prg_map[n + 8] = 1024 * n;
        }
        for (n = 1; n <= 32; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 0;
        }
        this.setprgregs();
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        if ((n & 1) != 0) {
            if (n >= 32768 && n <= 40959) {
                switch (this.whichbank) {
                    case 0: 
                    case 1: 
                    case 2: 
                    case 3: 
                    case 4: 
                    case 5: {
                        this.chrreg[this.whichbank] = n2;
                        this.setupchr();
                        break;
                    }
                    case 6: {
                        this.prgreg0 = n2;
                        this.setprgregs();
                        break;
                    }
                    case 7: {
                        this.prgreg1 = n2;
                        this.setprgregs();
                        break;
                    }
                    case 8: 
                    case 9: {
                        this.chrreg[this.whichbank - 2] = n2;
                        break;
                    }
                    case 15: {
                        this.prgreg2 = n2;
                        this.setprgregs();
                    }
                }
            } else if (n < 40960 || n > 49151) {
                if (n >= 49152 && n <= 57343) {
                    this.irqreload = true;
                    this.irqmode = (n2 & 1) != 0;
                } else if (n >= 57344 && n <= 65535) {
                    this.irqenable = true;
                }
            }
        } else if (n >= 32768 && n <= 40959) {
            this.whichbank = n2 & 0xF;
            this.chrmode1k = (n2 & 0x20) != 0;
            this.prgconfig = (n2 & 0x40) != 0;
            this.chrconfig = (n2 & 0x80) != 0;
            this.setupchr();
            this.setprgregs();
        } else if (n >= 40960 && n <= 49151) {
            if (this.scrolltype != Mapper.MirrorType.FOUR_SCREEN_MIRROR) {
                this.setmirroring((n2 & 1) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
            }
        } else if (n >= 49152 && n <= 57343) {
            this.irqctrreload = n2;
            this.irqreload = true;
        } else if (n >= 57344 && n <= 65535) {
            if (this.interrupted) {
                --this.cpu.interrupt;
            }
            this.interrupted = false;
            this.irqenable = false;
            this.irqctr = this.irqctrreload;
        }
    }

    private void setupchr() {
        if (this.chrconfig) {
            if (this.chrmode1k) {
                this.setppubank(1, 0, this.chrreg[2]);
                this.setppubank(1, 1, this.chrreg[3]);
                this.setppubank(1, 2, this.chrreg[4]);
                this.setppubank(1, 3, this.chrreg[5]);
                this.setppubank(1, 4, this.chrreg[0]);
                this.setppubank(1, 5, this.chrreg[6]);
                this.setppubank(1, 6, this.chrreg[1]);
                this.setppubank(1, 7, this.chrreg[7]);
            } else {
                this.setppubank(1, 0, this.chrreg[2]);
                this.setppubank(1, 1, this.chrreg[3]);
                this.setppubank(1, 2, this.chrreg[4]);
                this.setppubank(1, 3, this.chrreg[5]);
                this.setppubank(2, 4, this.chrreg[0] >> 1 << 1);
                this.setppubank(2, 6, this.chrreg[1] >> 1 << 1);
            }
        } else if (this.chrmode1k) {
            this.setppubank(1, 0, this.chrreg[0]);
            this.setppubank(1, 1, this.chrreg[6]);
            this.setppubank(1, 2, this.chrreg[1]);
            this.setppubank(1, 3, this.chrreg[7]);
            this.setppubank(1, 4, this.chrreg[2]);
            this.setppubank(1, 5, this.chrreg[3]);
            this.setppubank(1, 6, this.chrreg[4]);
            this.setppubank(1, 7, this.chrreg[5]);
        } else {
            this.setppubank(1, 4, this.chrreg[2]);
            this.setppubank(1, 5, this.chrreg[3]);
            this.setppubank(1, 6, this.chrreg[4]);
            this.setppubank(1, 7, this.chrreg[5]);
            this.setppubank(2, 0, this.chrreg[0] >> 1 << 1);
            this.setppubank(2, 2, this.chrreg[1] >> 1 << 1);
        }
    }

    private void setprgregs() {
        if (!this.prgconfig) {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i] = 1024 * (i + this.prgreg0 * 8) % this.prgsize;
                this.prg_map[i + 8] = 1024 * (i + this.prgreg1 * 8) % this.prgsize;
                this.prg_map[i + 16] = 1024 * (i + this.prgreg2 * 8) % this.prgsize;
            }
        } else {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i] = 1024 * (i + this.prgreg2 * 8) % this.prgsize;
                this.prg_map[i + 8] = 1024 * (i + this.prgreg0 * 8) % this.prgsize;
                this.prg_map[i + 16] = 1024 * (i + this.prgreg1 * 8) % this.prgsize;
            }
        }
    }

    @Override
    public void notifyscanline(int n) {
        if (this.irqmode) {
            return;
        }
        if (n > 239 && n != 261) {
            return;
        }
        if (!this.ppu.mmc3CounterClocking()) {
            return;
        }
        this.clockscanlinecounter();
    }

    @Override
    public void cpucycle(int n) {
        if (this.intnextcycle) {
            this.intnextcycle = false;
            if (!this.interrupted) {
                ++this.cpu.interrupt;
                this.interrupted = true;
            }
        }
        if (!this.irqmode) {
            return;
        }
        this.remainder += n;
        for (int i = 0; i < this.remainder; ++i) {
            if ((i & 3) != 0) continue;
            this.clockscanlinecounter();
        }
        this.remainder %= 4;
    }

    public void clockscanlinecounter() {
        if (this.irqreload) {
            this.irqreload = false;
            this.irqctr = this.irqctrreload + 1;
        } else if (this.irqctr == 0) {
            this.irqctr = this.irqctrreload;
        } else if (--this.irqctr == 0 && this.irqenable) {
            this.intnextcycle = true;
        }
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) % this.chrsize;
        }
    }
}

