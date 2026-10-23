/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.audio.Namco163SoundChip;
import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;
import java.util.Arrays;

public class NamcoMapper
extends Mapper {
    private int soundAddr = 0;
    private boolean autoincrement = false;
    private boolean irqenable = false;
    private boolean interrupted = false;
    private boolean chrramenable0 = false;
    private boolean chrramenable1 = false;
    Namco163SoundChip sound = new Namco163SoundChip();
    private boolean hasInitSound = false;
    private int irqcounter = 16383;
    private int[] chrbanks = new int[8];
    private int[] chr_ram = new int[16384];

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
    public int cartRead(int n) {
        if (n >= 18432 && n < 20480) {
            if (!this.hasInitSound) {
                this.cpuram.apu.addExpnSound(this.sound);
                this.hasInitSound = true;
            }
            int n2 = this.sound.read(this.soundAddr);
            if (this.autoincrement) {
                ++this.soundAddr;
                this.soundAddr &= 0x7F;
            }
            return n2;
        }
        if (n < 22528) {
            this.irqack();
            return this.irqcounter & 0xFF;
        }
        if (n < 24576) {
            this.irqack();
            return this.irqcounter >> 8 & 0x7F | (this.irqenable ? 128 : 0);
        }
        if (n >= 32768) {
            return this.prg[this.prg_map[(n & Short.MAX_VALUE) >> 10] + (n & 0x3FF)];
        }
        if (n >= 24576 && this.hasprgram) {
            return this.prgram[n & 0x1FFF];
        }
        return n >> 8;
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n < 18432 || n >= 24576 && n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        if (n <= 20479) {
            if (!this.hasInitSound) {
                this.cpuram.apu.addExpnSound(this.sound);
                this.hasInitSound = true;
            }
            this.sound.write(this.soundAddr, n2);
            if (this.autoincrement) {
                ++this.soundAddr;
                this.soundAddr &= 0x7F;
            }
        } else if (n <= 22527) {
            this.irqcounter &= 0x7F00;
            this.irqcounter |= n2;
            this.irqack();
        } else if (n <= 24575) {
            this.irqcounter &= 0xFF;
            this.irqcounter |= (n2 & 0x7F) << 8;
            this.irqenable = (n2 & 0x80) != 0;
            this.irqack();
        } else if (n <= 49151) {
            int n3 = n >> 11 & 7;
            this.setppubank(1, n3, n2);
            this.chrbanks[n3] = n2;
        } else if (n <= 51199) {
            this.nt0 = n2 < 224 ? Arrays.copyOfRange(this.chr, n2 * 1024, (n2 + 1) * 1024) : ((n2 & 1) != 0 ? this.pput1 : this.pput0);
        } else if (n <= 51455) {
            this.nt1 = n2 < 224 ? Arrays.copyOfRange(this.chr, n2 * 1024, (n2 + 1) * 1024) : ((n2 & 1) != 0 ? this.pput1 : this.pput0);
        } else if (n <= 55295) {
            this.nt2 = n2 < 224 ? Arrays.copyOfRange(this.chr, n2 * 1024, (n2 + 1) * 1024) : ((n2 & 1) != 0 ? this.pput1 : this.pput0);
        } else if (n <= 57343) {
            this.nt3 = n2 < 224 ? Arrays.copyOfRange(this.chr, n2 * 1024, (n2 + 1) * 1024) : ((n2 & 1) != 0 ? this.pput1 : this.pput0);
        } else if (n <= 59391) {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i] = 1024 * (i + 8 * (n2 & 0x3F)) % this.prgsize;
            }
        } else if (n <= 61439) {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i + 8] = 1024 * (i + 8 * (n2 & 0x3F)) % this.prgsize;
            }
            this.chrramenable0 = (n2 & 0x40) == 0;
            this.chrramenable1 = (n2 & 0x80) == 0;
        } else if (n <= 63487) {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i + 16] = 1024 * (i + 8 * (n2 & 0x3F)) % this.prgsize;
            }
        } else if (n <= 65535) {
            this.autoincrement = (n2 & 0x80) != 0;
            this.soundAddr = n2 & 0x7F;
        }
    }

    private void irqack() {
        if (this.interrupted) {
            --this.cpu.interrupt;
            this.interrupted = false;
        }
    }

    @Override
    public void cpucycle(int n) {
        this.irqcounter += n;
        if (this.irqcounter > Short.MAX_VALUE) {
            this.irqcounter = Short.MAX_VALUE;
        }
        if (this.irqcounter == Short.MAX_VALUE && this.irqenable && !this.interrupted) {
            ++this.cpu.interrupt;
            this.interrupted = true;
        }
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) & this.chrsize - 1;
        }
    }

    @Override
    public int ppuRead(int n) {
        if (n < 4096) {
            if (this.chrramenable0 && this.chrbanks[n >> 10] > 224) {
                return this.chr_ram[this.chr_map[n >> 10] + (n & 0x3FF)];
            }
            return this.chr[this.chr_map[n >> 10] + (n & 0x3FF)];
        }
        if (n < 8192) {
            if (this.chrramenable1 && this.chrbanks[n >> 10] > 224) {
                return this.chr_ram[this.chr_map[n >> 10] - 229376 + (n & 0x3FF)];
            }
            return this.chr[this.chr_map[n >> 10] + (n & 0x3FF)];
        }
        return super.ppuRead(n);
    }

    @Override
    public void ppuWrite(int n, int n2) {
        if ((n &= 0x3FFF) < 4096) {
            if (this.chrramenable0 && this.chrbanks[n >> 10] > 224) {
                this.chr_ram[this.chr_map[n >> 10] - 229376 + (n & 0x3FF)] = n2;
            } else {
                this.chr[this.chr_map[n >> 10] + (n & 0x3FF)] = n2;
            }
        } else if (n < 8192) {
            if (this.chrramenable1 && this.chrbanks[n >> 10] > 224) {
                this.chr_ram[this.chr_map[n >> 10] - 229376 + (n & 0x3FF)] = n2;
            } else {
                this.chr[this.chr_map[n >> 10] + (n & 0x3FF)] = n2;
            }
        } else {
            super.ppuWrite(n, n2);
        }
    }
}

