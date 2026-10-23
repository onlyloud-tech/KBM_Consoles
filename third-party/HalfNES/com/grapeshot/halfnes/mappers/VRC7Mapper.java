/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.audio.ExpansionSoundChip;
import com.grapeshot.halfnes.audio.VRC7SoundChip;
import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class VRC7Mapper
extends Mapper {
    int prgbank0;
    int prgbank1;
    int prgbank2;
    int[] chrbank = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
    boolean irqmode;
    boolean irqenable;
    boolean irqack;
    boolean firedinterrupt = false;
    int irqreload;
    int irqcounter = 22;
    int regaddr = 0;
    ExpansionSoundChip sndchip = new VRC7SoundChip();
    boolean hasInitSound = false;
    int prescaler = 341;

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 1; n <= 32; ++n) {
            this.prg_map[32 - n] = (this.prgsize - 1024 * n) % this.prgsize;
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
        boolean bl = (n & 0x10) != 0 | (n & 8) != 0;
        boolean bl2 = (n & 0x20) != 0;
        switch (n >> 12) {
            case 8: {
                if (bl) {
                    this.prgbank1 = n2;
                } else {
                    this.prgbank0 = n2;
                }
                this.setbanks();
                break;
            }
            case 9: {
                if (!bl && !bl2) {
                    this.prgbank2 = n2;
                    this.setbanks();
                    break;
                }
                if (bl && bl2) {
                    if (!this.hasInitSound) {
                        this.cpuram.apu.addExpnSound(this.sndchip);
                        this.hasInitSound = true;
                    }
                    this.sndchip.write(this.regaddr, n2);
                    break;
                }
                this.regaddr = n2;
                break;
            }
            case 10: {
                this.chrbank[bl ? 1 : 0] = n2;
                this.setbanks();
                break;
            }
            case 11: {
                this.chrbank[(bl ? 1 : 0) + 2] = n2;
                this.setbanks();
                break;
            }
            case 12: {
                this.chrbank[(bl ? 1 : 0) + 4] = n2;
                this.setbanks();
                break;
            }
            case 13: {
                this.chrbank[(bl ? 1 : 0) + 6] = n2;
                this.setbanks();
                break;
            }
            case 14: {
                if (bl) {
                    this.irqreload = n2;
                    break;
                }
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
                break;
            }
            case 15: {
                if (bl) {
                    this.irqenable = this.irqack;
                    if (this.firedinterrupt) {
                        --this.cpu.interrupt;
                    }
                    this.firedinterrupt = false;
                    break;
                }
                this.irqack = (n2 & 1) != 0;
                this.irqenable = (n2 & 2) != 0;
                boolean bl3 = this.irqmode = (n2 & 4) != 0;
                if (this.irqenable) {
                    this.irqcounter = this.irqreload;
                    this.prescaler = 341;
                }
                if (this.firedinterrupt) {
                    --this.cpu.interrupt;
                }
                this.firedinterrupt = false;
            }
        }
    }

    private void setbanks() {
        int n;
        for (n = 1; n <= 8; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.prg_map[n] = 1024 * (n + 8 * this.prgbank0) % this.prgsize;
        }
        for (n = 0; n < 8; ++n) {
            this.prg_map[n + 8] = 1024 * (n + 8 * this.prgbank1) % this.prgsize;
        }
        for (n = 0; n < 8; ++n) {
            this.prg_map[n + 16] = 1024 * (n + 8 * this.prgbank2) % this.prgsize;
        }
        for (n = 0; n < 8; ++n) {
            this.setppubank(1, n, this.chrbank[n]);
        }
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) % this.chrsize;
        }
    }

    @Override
    public void cpucycle(int n) {
        if (this.irqenable) {
            if (this.irqmode) {
                this.scanlinecount();
            } else {
                this.prescaler -= 3;
                if (this.prescaler <= 0) {
                    this.prescaler += 341;
                    this.scanlinecount();
                }
            }
        }
    }

    public void scanlinecount() {
        if (this.irqenable) {
            if (this.irqcounter == 255) {
                this.irqcounter = this.irqreload;
                if (!this.firedinterrupt) {
                    ++this.cpu.interrupt;
                }
                this.firedinterrupt = true;
            } else {
                ++this.irqcounter;
            }
        }
    }
}

