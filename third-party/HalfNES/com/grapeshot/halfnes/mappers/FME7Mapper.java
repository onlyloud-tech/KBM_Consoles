/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.audio.ExpansionSoundChip;
import com.grapeshot.halfnes.audio.Sunsoft5BSoundChip;
import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class FME7Mapper
extends Mapper {
    private int commandRegister = 0;
    private int soundCommand = 0;
    private int[] charbanks = new int[8];
    private int[] prgbanks = new int[4];
    private boolean ramEnable = true;
    private boolean ramSelect = false;
    private int irqcounter = 65535;
    private boolean irqenabled;
    private boolean irqclock;
    private boolean hasInitSound = false;
    private final ExpansionSoundChip sndchip = new Sunsoft5BSoundChip();
    private boolean interrupted = false;

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        this.prg_map = new int[40];
        for (n = 1; n <= 40; ++n) {
            this.prg_map[40 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 0;
        }
    }

    @Override
    public final int cartRead(int n) {
        if (n >= 24576) {
            if (n < 32768 && this.ramSelect) {
                if (this.ramEnable) {
                    return this.prgram[n - 24576];
                }
                return n >> 8;
            }
            return this.prg[this.prg_map[n - 24576 >> 10] + (n & 0x3FF)];
        }
        return n >> 8;
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        if (n == 32768) {
            this.commandRegister = n2 & 0xF;
        } else if (n == 49152) {
            this.soundCommand = n2 & 0xF;
            if (!this.hasInitSound) {
                this.cpuram.apu.addExpnSound(this.sndchip);
                this.hasInitSound = true;
            }
        } else if (n == 40960) {
            switch (this.commandRegister) {
                case 0: 
                case 1: 
                case 2: 
                case 3: 
                case 4: 
                case 5: 
                case 6: 
                case 7: {
                    this.charbanks[this.commandRegister] = n2;
                    this.setbanks();
                    break;
                }
                case 8: {
                    this.ramEnable = (n2 & 0x80) != 0;
                    this.ramSelect = (n2 & 0x40) != 0;
                    this.prgbanks[0] = n2 & 0x3F;
                    this.setbanks();
                    break;
                }
                case 9: 
                case 10: 
                case 11: {
                    this.prgbanks[this.commandRegister - 8] = n2;
                    this.setbanks();
                    break;
                }
                case 12: {
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
                }
                case 13: {
                    this.irqclock = (n2 & 0x80) != 0;
                    boolean bl = this.irqenabled = (n2 & 1) != 0;
                    if (this.interrupted && this.cpu.interrupt > 0) {
                        --this.cpu.interrupt;
                    }
                    this.interrupted = false;
                    break;
                }
                case 14: {
                    this.irqcounter &= 0xFF00;
                    this.irqcounter |= n2;
                    break;
                }
                case 15: {
                    this.irqcounter &= 0xFF;
                    this.irqcounter |= n2 << 8;
                }
            }
        } else if (n == 57344) {
            this.sndchip.write(this.soundCommand, n2);
        }
    }

    @Override
    public void cpucycle(int n) {
        if (this.irqclock) {
            if (this.irqcounter == 0) {
                this.irqcounter = 65535;
                if (this.irqenabled && !this.interrupted) {
                    this.interrupted = true;
                    ++this.cpu.interrupt;
                }
            } else {
                --this.irqcounter;
            }
        }
    }

    private void setbanks() {
        int n;
        for (n = 0; n < 8; ++n) {
            for (int i = 0; i < 4; ++i) {
                this.prg_map[n + 8 * i] = 1024 * (n + this.prgbanks[i] * 8) % this.prgsize;
            }
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * this.charbanks[n] % this.chrsize;
        }
    }
}

