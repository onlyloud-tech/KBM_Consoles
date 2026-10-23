/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.audio.MMC5SoundChip;
import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;
import com.grapeshot.halfnes.utils;
import java.util.Arrays;

public class MMC5Mapper
extends Mapper {
    final int[] exram = new int[1024];
    private int exramMode;
    private int chrMode;
    private int prgMode;
    private int wramWrite1;
    private int wramWrite2;
    private int multiplier1;
    private int multiplier2;
    private int prgpage;
    private int chrOr;
    private int wrambank;
    boolean scanctrEnable;
    boolean irqPend;
    private final int[] chrregsA = new int[8];
    private final int[] chrregsB = new int[4];
    private final int[] prgregs = new int[4];
    private final int[] chrmapB = new int[4];
    private final boolean[] romHere = new boolean[3];
    private int scanctrLine;
    private int irqCounter = 20;
    private final int[] fillnt = new int[1024];
    private MMC5SoundChip soundchip;
    private boolean inFrame = false;
    private int fetchcount;
    private int exlatch;
    private int lastfetch;
    private int prevfetch;
    private int prevprevfetch;
    private boolean spritemode = false;

    @Override
    public void loadrom() throws BadMapperException {
        super.loadrom();
        this.prgregs[3] = this.prgsize / 8192 - 1;
        this.prgregs[2] = this.prgsize / 8192 - 1;
        this.prgregs[1] = this.prgsize / 8192 - 1;
        this.prgregs[0] = this.prgsize / 8192 - 1;
        this.prgMode = 3;
        this.setupPRG();
        for (int i = 0; i < 8; ++i) {
            this.chr_map[i] = 1024 * i;
        }
        this.prgram = new int[65536];
    }

    @Override
    public final void cartWrite(int n, int n2) {
        block49: {
            block48: {
                if (n >= 23552) break block48;
                switch (n) {
                    case 20480: 
                    case 20481: 
                    case 20482: 
                    case 20483: 
                    case 20484: 
                    case 20485: 
                    case 20486: 
                    case 20487: 
                    case 20496: 
                    case 20497: 
                    case 20501: {
                        if (this.soundchip == null) {
                            this.soundchip = new MMC5SoundChip();
                            this.cpuram.apu.addExpnSound(this.soundchip);
                        }
                        this.soundchip.write(n - 20480, n2);
                        break;
                    }
                    case 20736: {
                        this.prgMode = n2 & 3;
                        this.setupPRG();
                        break;
                    }
                    case 20737: {
                        this.chrMode = n2 & 3;
                        this.setupCHR();
                        break;
                    }
                    case 20738: {
                        this.wramWrite1 = n2;
                        break;
                    }
                    case 20739: {
                        this.wramWrite2 = n2;
                        break;
                    }
                    case 20740: {
                        this.exramMode = n2 & 3;
                        break;
                    }
                    case 20741: {
                        this.setMirroring(n2, this.exram);
                        break;
                    }
                    case 20742: {
                        Arrays.fill(this.fillnt, 0, 960, n2);
                        break;
                    }
                    case 20743: {
                        Arrays.fill(this.fillnt, 960, this.fillnt.length, n2 & 3 + (n2 & 3) << 2 + (n2 & 3) << 4 + (n2 & 3) << 6);
                        break;
                    }
                    case 20755: {
                        this.wrambank = n2 & 7;
                        break;
                    }
                    case 20756: {
                        this.prgregs[0] = n2 & 0x7F;
                        this.romHere[0] = (n2 & 0x80) != 0;
                        this.setupPRG();
                        break;
                    }
                    case 20757: {
                        this.prgregs[1] = n2 & 0x7F;
                        this.romHere[1] = (n2 & 0x80) != 0;
                        this.setupPRG();
                        break;
                    }
                    case 20758: {
                        this.prgregs[2] = n2 & 0x7F;
                        this.romHere[2] = (n2 & 0x80) != 0;
                        this.setupPRG();
                        break;
                    }
                    case 20759: {
                        this.prgregs[3] = n2 & 0x7F;
                        this.setupPRG();
                        break;
                    }
                    case 20768: {
                        this.chrregsA[0] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20769: {
                        this.chrregsA[1] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20770: {
                        this.chrregsA[2] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20771: {
                        this.chrregsA[3] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20772: {
                        this.chrregsA[4] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20773: {
                        this.chrregsA[5] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20774: {
                        this.chrregsA[6] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20775: {
                        this.chrregsA[7] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20776: {
                        this.chrregsB[0] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20777: {
                        this.chrregsB[1] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20778: {
                        this.chrregsB[2] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20779: {
                        this.chrregsB[3] = n2 | this.chrOr;
                        this.setupCHR();
                        break;
                    }
                    case 20784: {
                        this.chrOr = (n2 & 3) << 8;
                        break;
                    }
                    case 20992: {
                        if ((n2 & 0x80) != 0) {
                            System.err.println("Split screen mode not supported yet");
                            break;
                        }
                        break block49;
                    }
                    case 20993: {
                        break;
                    }
                    case 20994: {
                        break;
                    }
                    case 20995: {
                        this.scanctrLine = n2;
                        break;
                    }
                    case 20996: {
                        this.scanctrEnable = (n2 & 0x80) != 0;
                        break;
                    }
                    case 20997: {
                        this.multiplier1 = n2;
                        break;
                    }
                    case 20998: {
                        this.multiplier2 = n2;
                        break;
                    }
                }
                break block49;
            }
            if (n < 24576) {
                this.exram[n - 23552] = n2;
            } else if (n < 32768) {
                int n3 = this.wrambank * 8192 + (n - 24576);
                this.prgram[n3] = n2;
            } else if (n < 40960 && !this.romHere[0] && this.prgMode == 3) {
                System.err.println("RAM write to 0x8000 area");
                this.prgram[(this.prgregs[0] & 7) * 8192 + (n - 32768)] = n2;
            } else if (n < 49152 && !this.romHere[1]) {
                int n4 = this.prgMode == 3 ? 40960 : 32768;
                int n5 = this.prgMode == 3 ? this.prgregs[1] & 7 : (this.prgregs[1] & 7) >> 1;
                int n6 = n5 * (this.prgMode == 3 ? 8192 : 16384) + (n - n4);
                this.prgram[n6] = n2;
            } else if (n < 57344 && !this.romHere[2]) {
                System.err.println("RAM write to 0xC000 area " + utils.hex(n));
                this.prgram[(this.prgregs[2] & 7) * 8192 + (n - 49152)] = n2;
            } else {
                System.err.println("unsupported mmc5 write " + utils.hex(n) + this.romHere[0] + this.romHere[1] + this.romHere[2] + this.prgMode);
            }
        }
    }

    @Override
    public final int cartRead(int n) {
        if (!this.ppu.renderingOn() || this.ppu.scanline > 241) {
            this.inFrame = false;
        }
        if (n >= 32768) {
            if (this.prgMode == 0 || this.prgMode == 1 && (n >= 49152 || this.romHere[1]) || this.prgMode == 2 && (n >= 57344 || n >= 49152 && this.romHere[2] || this.romHere[1]) || this.prgMode == 3 && (n >= 57344 || n >= 49152 && this.romHere[2] || n >= 40960 && this.romHere[1] || this.romHere[0])) {
                return this.prg[this.prg_map[(n & Short.MAX_VALUE) >> 10] + (n & 0x3FF)];
            }
            System.err.println("MMC5 wants RAM at " + utils.hex(n));
            return 65535;
        }
        if (n >= 24576) {
            int n2 = this.wrambank * 8192 + (n - 24576);
            return this.prgram[n2];
        }
        if (n >= 23552) {
            return this.exram[n - 23552];
        }
        switch (n) {
            case 20501: {
                if (this.soundchip != null) {
                    return this.soundchip.status();
                }
                return n >> 8;
            }
            case 20996: {
                int n3 = (this.irqPend ? 128 : 0) + (this.inFrame ? 64 : 0);
                if (this.irqPend) {
                    this.irqPend = false;
                    --this.cpu.interrupt;
                }
                return n3;
            }
            case 20997: {
                return this.multiplier1 * this.multiplier2 & 0xFF;
            }
            case 20998: {
                return this.multiplier1 * this.multiplier2 >> 8 & 0xFF;
            }
        }
        return n >> 8;
    }

    public void setupPRG() {
        switch (this.prgMode) {
            default: {
                this.setcpubank(32, 0, (this.prgregs[3] & 0x7F) >> 2);
                break;
            }
            case 1: {
                this.setcpubank(16, 16, (this.prgregs[3] & 0x7F) >> 1);
                this.setcpubank(16, 0, (this.prgregs[1] & 0x7F) >> 1);
                break;
            }
            case 2: {
                this.setcpubank(8, 24, this.prgregs[3] & 0x7F);
                this.setcpubank(8, 16, this.prgregs[2] & 0x7F);
                this.setcpubank(8, 8, this.prgregs[1] & 0x7F | 1);
                this.setcpubank(8, 0, this.prgregs[1] & 0x7E);
                break;
            }
            case 3: {
                this.setcpubank(8, 24, this.prgregs[3] & 0x7F);
                this.setcpubank(8, 16, this.prgregs[2] & 0x7F);
                this.setcpubank(8, 8, this.prgregs[1] & 0x7F);
                this.setcpubank(8, 0, this.prgregs[0] & 0x7F);
            }
        }
    }

    public void setupCHR() {
        switch (this.chrMode) {
            default: {
                this.setppubank(8, 0, this.chrregsA[7]);
                this.setppubankB(4, 0, this.chrregsB[3]);
                break;
            }
            case 1: {
                this.setppubank(4, 4, this.chrregsA[7]);
                this.setppubank(4, 0, this.chrregsA[3]);
                this.setppubankB(4, 0, this.chrregsB[3]);
                break;
            }
            case 2: {
                this.setppubank(2, 6, this.chrregsA[7]);
                this.setppubank(2, 4, this.chrregsA[5]);
                this.setppubank(2, 2, this.chrregsA[3]);
                this.setppubank(2, 0, this.chrregsA[1]);
                this.setppubankB(2, 2, this.chrregsB[3]);
                this.setppubankB(2, 0, this.chrregsB[1]);
                break;
            }
            case 3: {
                this.setppubank(1, 7, this.chrregsA[7]);
                this.setppubank(1, 6, this.chrregsA[6]);
                this.setppubank(1, 5, this.chrregsA[5]);
                this.setppubank(1, 4, this.chrregsA[4]);
                this.setppubank(1, 3, this.chrregsA[3]);
                this.setppubank(1, 2, this.chrregsA[2]);
                this.setppubank(1, 1, this.chrregsA[1]);
                this.setppubank(1, 0, this.chrregsA[0]);
                this.setppubankB(1, 3, this.chrregsB[3]);
                this.setppubankB(1, 2, this.chrregsB[2]);
                this.setppubankB(1, 1, this.chrregsB[1]);
                this.setppubankB(1, 0, this.chrregsB[0]);
            }
        }
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) % this.chrsize;
        }
    }

    private void setppubankB(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chrmapB[i + n2] = 1024 * (n3 + i) % this.chrsize;
        }
    }

    private void setcpubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.prg_map[i + n2] = 1024 * (n3 * n + i) & this.prgsize - 1;
        }
    }

    @Override
    public int ppuRead(int n) {
        if (n < 8192) {
            if (++this.fetchcount == 3) {
                this.spritemode = true;
            }
            if (this.spritemode) {
                return this.chr[this.chr_map[n >> 10] + (n & 0x3FF)];
            }
            if (this.exramMode == 1) {
                if (this.exlatch == 2) {
                    ++this.exlatch;
                    return this.chr[(this.chrOr * 1024 | (this.exram[this.lastfetch] & 0x3F) * 4096 | n & 0xFFF) % this.chr.length];
                }
                if (this.exlatch == 3) {
                    this.exlatch = 0;
                    return this.chr[(this.chrOr * 1024 | (this.exram[this.lastfetch] & 0x3F) * 4096 | n & 0xFFF) % this.chr.length];
                }
            }
            return this.chr[this.chrmapB[n >> 10 & 3] + (n & 0x3FF)];
        }
        if (this.prevfetch == this.prevprevfetch && this.prevprevfetch == n) {
            this.incScanline();
            this.exlatch = 0;
        }
        this.prevprevfetch = this.prevfetch;
        this.prevfetch = n;
        this.spritemode = false;
        this.fetchcount = 0;
        if (this.exramMode == 1) {
            if (this.exlatch == 0) {
                ++this.exlatch;
                this.lastfetch = n & 0x3FF;
            } else if (this.exlatch == 1) {
                ++this.exlatch;
                int n2 = this.exram[this.lastfetch];
                return (n2 & 0xC0) >> 6 | (n2 & 0xC0) >> 4 | (n2 & 0xC0) >> 2 | n2 & 0xC0;
            }
        }
        return super.ppuRead(n);
    }

    public void incScanline() {
        if (!this.inFrame) {
            this.inFrame = true;
            this.irqCounter = 0;
            if (this.irqPend) {
                this.irqPend = false;
                --this.cpu.interrupt;
            }
        } else {
            if (this.irqCounter++ == this.scanctrLine) {
                this.irqPend = true;
            }
            if (this.irqPend && this.scanctrEnable) {
                ++this.cpu.interrupt;
            }
        }
    }

    public void setMirroring(int n, int[] nArray) {
        switch (n & 3) {
            default: {
                this.nt0 = this.pput0;
                break;
            }
            case 1: {
                this.nt0 = this.pput1;
                break;
            }
            case 2: {
                this.nt0 = nArray;
                break;
            }
            case 3: {
                this.nt0 = this.fillnt;
            }
        }
        switch ((n >>= 2) & 3) {
            default: {
                this.nt1 = this.pput0;
                break;
            }
            case 1: {
                this.nt1 = this.pput1;
                break;
            }
            case 2: {
                this.nt1 = nArray;
                break;
            }
            case 3: {
                this.nt1 = this.fillnt;
            }
        }
        switch ((n >>= 2) & 3) {
            default: {
                this.nt2 = this.pput0;
                break;
            }
            case 1: {
                this.nt2 = this.pput1;
                break;
            }
            case 2: {
                this.nt2 = nArray;
                break;
            }
            case 3: {
                this.nt2 = this.fillnt;
            }
        }
        switch ((n >>= 2) & 3) {
            default: {
                this.nt3 = this.pput0;
                break;
            }
            case 1: {
                this.nt3 = this.pput1;
                break;
            }
            case 2: {
                this.nt3 = nArray;
                break;
            }
            case 3: {
                this.nt3 = this.fillnt;
            }
        }
    }
}

