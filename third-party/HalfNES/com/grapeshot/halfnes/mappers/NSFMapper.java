/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.CPU;
import com.grapeshot.halfnes.CPURAM;
import com.grapeshot.halfnes.PPU;
import com.grapeshot.halfnes.audio.FDSSoundChip;
import com.grapeshot.halfnes.audio.MMC5SoundChip;
import com.grapeshot.halfnes.audio.Namco163SoundChip;
import com.grapeshot.halfnes.audio.Sunsoft5BSoundChip;
import com.grapeshot.halfnes.audio.VRC6SoundChip;
import com.grapeshot.halfnes.audio.VRC7SoundChip;
import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;
import com.grapeshot.halfnes.mappers.NSFPlayerFont;
import com.grapeshot.halfnes.utils;
import java.util.Arrays;

public class NSFMapper
extends Mapper {
    private int load;
    private int init;
    private int play;
    private int song;
    private int numSongs;
    public boolean nsfBanking;
    public int[] nsfStartBanks = new int[10];
    public int[] nsfBanks = new int[10];
    private int sndchip;
    boolean vrc6 = false;
    boolean vrc7 = false;
    boolean mmc5 = false;
    boolean n163 = false;
    boolean s5b = false;
    boolean hasInitSound = false;
    boolean fds = false;
    private boolean n163autoincrement = false;
    private int n163soundAddr = 0;
    private int mmc5multiplier1;
    private int mmc5multiplier2;
    private int vrc7regaddr = 0;
    private int s5bSoundCommand = 0;
    private Namco163SoundChip n163Audio;
    private VRC6SoundChip vrc6Audio;
    private VRC7SoundChip vrc7Audio;
    private Sunsoft5BSoundChip s5bAudio;
    private MMC5SoundChip mmc5Audio;
    private static final String trackstr = "Track --- / ---          <-B A->";
    private FDSSoundChip fdsAudio;
    int control;
    int prevcontrol;
    int unfinishedcounter = 0;
    int time = 4;

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        int n2;
        this.loader.parseHeader();
        this.prgsize = this.loader.prgsize;
        this.mappertype = this.loader.mappertype;
        this.prgoff = this.loader.prgoff;
        for (n2 = 112; n2 < 120; ++n2) {
            if (this.loader.header[n2] == 0) continue;
            this.nsfBanking = true;
            this.nsfStartBanks[n2 - 112] = this.loader.header[n2];
        }
        this.prgoff = 0;
        this.load = this.loader.header[8] + (this.loader.header[9] << 8);
        this.init = this.loader.header[10] + (this.loader.header[11] << 8);
        this.play = this.loader.header[12] + (this.loader.header[13] << 8);
        this.numSongs = this.loader.header[6] - 1;
        this.song = this.loader.header[7] - 1;
        this.region = this.loader.header[122] == 1 ? Mapper.TVType.PAL : Mapper.TVType.NTSC;
        this.chroff = 0;
        this.chrsize = 0;
        this.scrolltype = Mapper.MirrorType.V_MIRROR;
        this.sndchip = this.loader.header[123];
        if (!this.nsfBanking && this.load < 32768) {
            throw new BadMapperException("NSF with no banking loading low");
        }
        n2 = this.nsfBanking ? this.load & 0xFFF : this.load - 32768;
        this.prg = new int[0x100000];
        System.arraycopy(this.loader.load(this.loader.romlen(), this.prgoff), 0, this.prg, n2, this.loader.romlen());
        this.crc = NSFMapper.crc32(this.prg);
        this.haschrram = true;
        this.chrsize = 8192;
        this.chr = new int[8192];
        this.prg_map = new int[(this.sndchip & 4) != 0 ? 40 : 32];
        if (!this.nsfBanking) {
            for (n = 0; n < 8; ++n) {
                this.nsfStartBanks[n] = n;
            }
        }
        if ((this.sndchip & 4) != 0) {
            this.nsfStartBanks[8] = this.nsfStartBanks[6];
            this.nsfStartBanks[9] = this.nsfStartBanks[7];
        }
        this.chr_map = new int[8];
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * n & this.chrsize - 1;
        }
        this.cpuram = new CPURAM(this);
        this.cpu = new CPU(this.cpuram);
        this.ppu = new PPU(this);
        Arrays.fill(this.pput0, 0);
        this.setmirroring(this.scrolltype);
        this.ppu.pal[0] = 63;
        this.ppu.pal[1] = 32 + (int)(this.crc % 12L);
        this.ppu.pal[2] = 32 + (int)(this.crc % 12L);
        this.ppu.pal[3] = 32 + (int)(this.crc % 12L);
        this.chr = NSFPlayerFont.font;
    }

    @Override
    public void init() {
        int n;
        this.nsfBanks = (int[])this.nsfStartBanks.clone();
        this.setBanks();
        for (n = 0; n <= 2047; ++n) {
            this.cpuram.write(n, 0);
        }
        for (n = 16384; n <= 16403; ++n) {
            this.cpuram.write(n, 0);
        }
        this.cpuram.write(16405, 15);
        this.cpuram.write(16407, 64);
        this.cpu.push(255);
        this.cpu.push(250);
        this.cpu.setPC(this.init);
        this.cpu.interrupt = -99999;
        this.cpu.setRegA(this.song);
        if (this.region == Mapper.TVType.PAL) {
            this.cpu.setRegX(1);
        } else {
            this.cpu.setRegX(0);
        }
        for (n = 0; n < 768; ++n) {
            this.pput0[n] = Math.random() > 0.5 ? 47 : 92;
        }
        for (n = 0; n < 96; ++n) {
            this.pput0[n + 800] = this.loader.header[n + 14];
        }
        for (n = 0; n < trackstr.length(); ++n) {
            this.pput0[n + 896] = trackstr.charAt(n);
        }
        if (!this.hasInitSound) {
            this.setSoundChip();
            this.hasInitSound = true;
        }
        if (!this.fds) {
            for (n = 24576; n <= Short.MAX_VALUE; ++n) {
                this.cpuram.write(n, 0);
            }
        }
    }

    @Override
    public void reset() {
        this.song = this.loader.header[7] - 1;
        this.init();
        this.cpu.setPC(this.init);
    }

    @Override
    public void cartWrite(int n, int n2) {
        if (this.n163 && n == 63488) {
            this.n163autoincrement = (n2 & 0x80) != 0;
            this.n163soundAddr = n2 & 0x7F;
        } else if (this.n163 && n == 18432) {
            this.n163Audio.write(this.n163soundAddr, n2);
            if (this.n163autoincrement) {
                ++this.n163soundAddr;
                this.n163soundAddr &= 0x7F;
            }
        } else if (this.s5b && n == 57344) {
            this.s5bAudio.write(this.s5bSoundCommand, n2);
        } else if (this.s5b && n == 49152) {
            this.s5bSoundCommand = n2 & 0xF;
        } else if (this.vrc6 && n >= 45056 && n <= 45058) {
            this.vrc6Audio.write((n & 0xF000) + (n & 3), n2);
        } else if (this.vrc6 && n >= 40960 && n <= 40962) {
            this.vrc6Audio.write((n & 0xF000) + (n & 3), n2);
        } else if (this.vrc7 && n == 36912) {
            this.vrc7Audio.write(this.vrc7regaddr, n2);
        } else if (this.vrc7 && n == 36880) {
            this.vrc7regaddr = n2;
        } else if (this.vrc6 && n >= 36864 && n <= 36866) {
            this.vrc6Audio.write((n & 0xF000) + (n & 3), n2);
        } else if (this.fds && this.nsfBanking && n >= 24576) {
            if (n < 32768) {
                int n3 = this.prg_map[(n - 24576 >> 10) + 32] + (n & 0x3FF);
                this.prg[n3] = n2;
            } else {
                int n4 = this.prg_map[(n & Short.MAX_VALUE) >> 10] + (n & 0x3FF);
                this.prg[n4] = n2;
            }
        } else if (this.fds && !this.nsfBanking && n >= 24576) {
            if (n < 32768) {
                this.prgram[n - 24576] = n2;
            } else {
                int n5 = this.prg_map[(n & Short.MAX_VALUE) >> 10] + (n & 0x3FF);
                this.prg[n5] = n2;
            }
        } else if (n >= 24576 && n < 32768) {
            this.prgram[n & 0x1FFF] = n2;
        } else if (n >= 24568 && n < 24576) {
            this.nsfBanks[n - 24568] = n2;
            this.setBanks();
        } else if (this.fds && this.nsfBanking && n == 24566) {
            this.nsfBanks[8] = n2;
            this.setBanks();
        } else if (this.fds && this.nsfBanking && n >= 24567) {
            this.nsfBanks[9] = n2;
            this.setBanks();
        } else if (this.mmc5 && n >= 23552 && n <= 24565) {
            this.prgram[n - 23552] = n2;
        } else if (this.mmc5 && n == 20998) {
            this.mmc5multiplier2 = n2;
        } else if (this.mmc5 && n == 20997) {
            this.mmc5multiplier1 = n2;
        } else if (this.mmc5 && n >= 20480 && n <= 20501) {
            this.mmc5Audio.write(n - 20480, n2);
        } else if (this.fds && n >= 16448 && n <= 16530) {
            this.fdsAudio.write(n, n2);
        }
    }

    @Override
    public int cartRead(int n) {
        if (n >= 32768) {
            if (n > 65530) {
                switch (n) {
                    case 65531: {
                        return 76;
                    }
                    case 65532: {
                        return 251;
                    }
                    case 65533: {
                        return 255;
                    }
                }
                return 0;
            }
            int n2 = this.prg_map[(n & Short.MAX_VALUE) >> 10] + (n & 0x3FF);
            return this.prg[n2];
        }
        if (n >= 24576 && this.hasprgram) {
            if (this.fds && this.nsfBanking) {
                int n3 = this.prg_map[(n - 24576 >> 10) + 32] + (n & 0x3FF);
                return this.prg[n3];
            }
            return this.prgram[n & 0x1FFF];
        }
        if (n >= 24568) {
            return this.nsfBanks[n - 24568];
        }
        if (this.fds && this.nsfBanking && n == 24566) {
            return this.nsfBanks[8];
        }
        if (this.fds && this.nsfBanking && n == 24567) {
            return this.nsfBanks[9];
        }
        if (this.mmc5 && n >= 23552) {
            return this.prgram[n - 23552];
        }
        if (this.mmc5 && n == 20998) {
            return this.mmc5multiplier1 * this.mmc5multiplier2 >> 8 & 0xFF;
        }
        if (this.mmc5 && n == 20997) {
            return this.mmc5multiplier1 * this.mmc5multiplier2 & 0xFF;
        }
        if (this.mmc5 && n == 20501) {
            return this.mmc5Audio.status();
        }
        if (this.n163 && n == 18432) {
            int n4 = this.n163Audio.read(this.n163soundAddr);
            if (this.n163autoincrement) {
                ++this.n163soundAddr;
                this.n163soundAddr &= 0x7F;
            }
            return n4;
        }
        if (this.fds && n >= 16448 && n < 16531) {
            return this.fdsAudio.read(n);
        }
        return n >> 8;
    }

    @Override
    public void ppuWrite(int n, int n2) {
    }

    @Override
    public void notifyscanline(int n) {
        if (n == 240) {
            if (this.cpu.PC != 65531) {
                if (this.unfinishedcounter < this.time) {
                    ++this.unfinishedcounter;
                    return;
                }
                if (this.unfinishedcounter == this.time) {
                    ++this.unfinishedcounter;
                }
            } else {
                this.unfinishedcounter = 0;
            }
            this.ppu.write(6, 0);
            this.ppu.write(6, 0);
            this.ppu.write(5, 0);
            this.ppu.write(0, 0);
            this.ppu.write(1, 26);
            this.writeTracks();
            this.prevcontrol = this.control;
            this.control = 0;
            this.cpuram.write(16406, 1);
            this.cpuram.write(16406, 0);
            for (int i = 0; i < 8; ++i) {
                this.control = (this.control << 1) + ((this.cpuram.read(16406) & 3) != 0 ? 1 : 0);
            }
            if ((this.control & 0x80) != 0 && (this.prevcontrol & 0x80) == 0) {
                ++this.song;
                if (this.song > this.numSongs) {
                    this.song = 0;
                }
                this.init();
            } else if ((this.control & 0x40) != 0 && (this.prevcontrol & 0x40) == 0) {
                --this.song;
                if (this.song < 0) {
                    this.song = this.numSongs;
                }
                this.init();
            } else if (this.unfinishedcounter <= this.time) {
                this.cpu.push(this.cpu.PC - 1 >> 8);
                this.cpu.push(this.cpu.PC - 1 & 0xFF);
                this.cpu.setPC(this.play);
            }
        }
    }

    @Override
    public String getrominfo() {
        return "NSF INFO: \nFilename:     " + this.loader.name + "\nSize:     " + this.loader.romlen() / 1024 + " K\nExpansion Sound:  " + this.expSound() + "\nTrack: " + (this.song + 1) + " / " + (this.numSongs + 1) + "\nLoad Address:  " + utils.hex(this.load) + "\nInit Address:  " + utils.hex(this.init) + "\nPlay Address:  " + utils.hex(this.play) + "\nBanking?      " + (this.nsfBanking ? "Yes" : "No") + "\nCRC:          " + utils.hex(this.crc);
    }

    private String expSound() {
        Object object = "";
        if (this.vrc6) {
            object = (String)object + "VRC6 ";
        }
        if (this.vrc7) {
            object = (String)object + "VRC7 ";
        }
        if (this.n163) {
            object = (String)object + "Namco 163 ";
        }
        if (this.mmc5) {
            object = (String)object + "MMC5 ";
        }
        if (this.s5b) {
            object = (String)object + "Sunsoft 5B ";
        }
        if (this.fds) {
            object = (String)object + "FDS ";
        }
        return ((String)object).length() > 0 ? (String)object : "None";
    }

    private void setBanks() {
        for (int i = 0; i < this.prg_map.length; ++i) {
            this.prg_map[i] = 4096 * this.nsfBanks[i / 4] + 1024 * (i % 4);
            if (this.prg_map[i] < this.prg.length) continue;
            int n = i;
            this.prg_map[n] = this.prg_map[n] % this.prg.length;
        }
    }

    private void setSoundChip() {
        if ((this.sndchip & 1) != 0) {
            this.vrc6 = true;
            this.vrc6Audio = new VRC6SoundChip();
            this.cpuram.apu.addExpnSound(this.vrc6Audio);
        }
        if ((this.sndchip & 2) != 0) {
            this.vrc7 = true;
            this.vrc7Audio = new VRC7SoundChip();
            this.cpuram.apu.addExpnSound(this.vrc7Audio);
        }
        if ((this.sndchip & 4) != 0) {
            this.fds = true;
            this.fdsAudio = new FDSSoundChip();
            this.cpuram.apu.addExpnSound(this.fdsAudio);
        }
        if ((this.sndchip & 8) != 0) {
            this.mmc5 = true;
            this.mmc5Audio = new MMC5SoundChip();
            this.cpuram.apu.addExpnSound(this.mmc5Audio);
        }
        if ((this.sndchip & 0x10) != 0) {
            this.n163 = true;
            this.n163Audio = new Namco163SoundChip();
            this.cpuram.apu.addExpnSound(this.n163Audio);
        }
        if ((this.sndchip & 0x20) != 0) {
            this.s5b = true;
            this.s5bAudio = new Sunsoft5BSoundChip();
            this.cpuram.apu.addExpnSound(this.s5bAudio);
        }
    }

    private void writeTracks() {
        String string = String.format("%3d / %-3d", this.song + 1, this.numSongs + 1);
        for (int i = 0; i < string.length(); ++i) {
            this.pput0[i + 896 + 6] = string.charAt(i);
        }
    }
}

