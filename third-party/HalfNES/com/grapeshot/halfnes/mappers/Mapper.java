/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.CPU;
import com.grapeshot.halfnes.CPURAM;
import com.grapeshot.halfnes.PPU;
import com.grapeshot.halfnes.PrefsSingleton;
import com.grapeshot.halfnes.ROMLoader;
import com.grapeshot.halfnes.mappers.Action52Mapper;
import com.grapeshot.halfnes.mappers.AfterburnerMapper;
import com.grapeshot.halfnes.mappers.AnromMapper;
import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.BnromMapper;
import com.grapeshot.halfnes.mappers.CaltronMapper;
import com.grapeshot.halfnes.mappers.CnromMapper;
import com.grapeshot.halfnes.mappers.CodemastersMapper;
import com.grapeshot.halfnes.mappers.ColorDreamsMapper;
import com.grapeshot.halfnes.mappers.CrazyClimberMapper;
import com.grapeshot.halfnes.mappers.CrimeBustersMapper;
import com.grapeshot.halfnes.mappers.FME7Mapper;
import com.grapeshot.halfnes.mappers.GnromMapper;
import com.grapeshot.halfnes.mappers.IremH3001Mapper;
import com.grapeshot.halfnes.mappers.MIMICMapper;
import com.grapeshot.halfnes.mappers.MMC1Mapper;
import com.grapeshot.halfnes.mappers.MMC2Mapper;
import com.grapeshot.halfnes.mappers.MMC3Mapper;
import com.grapeshot.halfnes.mappers.MMC4Mapper;
import com.grapeshot.halfnes.mappers.MMC5Mapper;
import com.grapeshot.halfnes.mappers.Mapper107;
import com.grapeshot.halfnes.mappers.Mapper112;
import com.grapeshot.halfnes.mappers.Mapper119;
import com.grapeshot.halfnes.mappers.Mapper140;
import com.grapeshot.halfnes.mappers.Mapper15;
import com.grapeshot.halfnes.mappers.Mapper152;
import com.grapeshot.halfnes.mappers.Mapper182;
import com.grapeshot.halfnes.mappers.Mapper185;
import com.grapeshot.halfnes.mappers.Mapper200;
import com.grapeshot.halfnes.mappers.Mapper201;
import com.grapeshot.halfnes.mappers.Mapper203;
import com.grapeshot.halfnes.mappers.Mapper212;
import com.grapeshot.halfnes.mappers.Mapper213;
import com.grapeshot.halfnes.mappers.Mapper214;
import com.grapeshot.halfnes.mappers.Mapper225;
import com.grapeshot.halfnes.mappers.Mapper226;
import com.grapeshot.halfnes.mappers.Mapper229;
import com.grapeshot.halfnes.mappers.Mapper231;
import com.grapeshot.halfnes.mappers.Mapper240;
import com.grapeshot.halfnes.mappers.Mapper241;
import com.grapeshot.halfnes.mappers.Mapper242;
import com.grapeshot.halfnes.mappers.Mapper244;
import com.grapeshot.halfnes.mappers.Mapper246;
import com.grapeshot.halfnes.mappers.Mapper255;
import com.grapeshot.halfnes.mappers.Mapper31;
import com.grapeshot.halfnes.mappers.Mapper33;
import com.grapeshot.halfnes.mappers.Mapper36;
import com.grapeshot.halfnes.mappers.Mapper47;
import com.grapeshot.halfnes.mappers.Mapper48;
import com.grapeshot.halfnes.mappers.Mapper58;
import com.grapeshot.halfnes.mappers.Mapper60;
import com.grapeshot.halfnes.mappers.Mapper61;
import com.grapeshot.halfnes.mappers.Mapper62;
import com.grapeshot.halfnes.mappers.Mapper70;
import com.grapeshot.halfnes.mappers.Mapper72;
import com.grapeshot.halfnes.mappers.Mapper76;
import com.grapeshot.halfnes.mappers.Mapper78;
import com.grapeshot.halfnes.mappers.Mapper86;
import com.grapeshot.halfnes.mappers.Mapper87;
import com.grapeshot.halfnes.mappers.Mapper92;
import com.grapeshot.halfnes.mappers.Mapper94;
import com.grapeshot.halfnes.mappers.Mapper97;
import com.grapeshot.halfnes.mappers.NINA_001_Mapper;
import com.grapeshot.halfnes.mappers.NINA_003_006_Mapper;
import com.grapeshot.halfnes.mappers.NSFMapper;
import com.grapeshot.halfnes.mappers.NamcoMapper;
import com.grapeshot.halfnes.mappers.Namcot34x3Mapper;
import com.grapeshot.halfnes.mappers.NromMapper;
import com.grapeshot.halfnes.mappers.Sunsoft01Mapper;
import com.grapeshot.halfnes.mappers.Sunsoft02Mapper;
import com.grapeshot.halfnes.mappers.Sunsoft03Mapper;
import com.grapeshot.halfnes.mappers.TengenRamboMapper;
import com.grapeshot.halfnes.mappers.UnromMapper;
import com.grapeshot.halfnes.mappers.VRC1Mapper;
import com.grapeshot.halfnes.mappers.VRC2Mapper;
import com.grapeshot.halfnes.mappers.VRC3Mapper;
import com.grapeshot.halfnes.mappers.VRC4Mapper;
import com.grapeshot.halfnes.mappers.VRC6Mapper;
import com.grapeshot.halfnes.mappers.VRC7Mapper;
import com.grapeshot.halfnes.utils;
import java.util.Arrays;
import java.util.prefs.Preferences;
import java.util.zip.CRC32;

public abstract class Mapper {
    protected ROMLoader loader;
    protected int mappertype;
    protected int submapper;
    protected int prgsize;
    protected int prgoff;
    protected int chroff;
    protected int chrsize;
    public CPU cpu;
    public CPURAM cpuram;
    public PPU ppu;
    protected int[] prg;
    protected int[] chr;
    protected int[] chr_map;
    protected int[] prg_map;
    protected int[] prgram = new int[8192];
    protected MirrorType scrolltype;
    protected boolean haschrram = false;
    protected boolean hasprgram = true;
    protected boolean savesram = false;
    protected final int[] pput0 = new int[1024];
    protected final int[] pput1 = new int[1024];
    protected final int[] pput2 = new int[1024];
    protected final int[] pput3 = new int[1024];
    protected int[] nt0;
    protected int[] nt1;
    protected int[] nt2;
    protected int[] nt3;
    long crc;
    TVType region;
    Preferences prefs = PrefsSingleton.get();

    public boolean supportsSaves() {
        return this.savesram;
    }

    public void destroy() {
        this.cpu = null;
        this.cpuram = null;
        this.ppu = null;
    }

    public static long crc32(int[] nArray) {
        CRC32 cRC32 = new CRC32();
        for (int n : nArray) {
            cRC32.update(n);
        }
        return cRC32.getValue();
    }

    public void loadrom() throws BadMapperException {
        int n;
        this.loader.parseHeader();
        this.prgsize = this.loader.prgsize;
        this.mappertype = this.loader.mappertype;
        this.prgoff = this.loader.prgoff;
        this.chroff = this.loader.chroff;
        this.chrsize = this.loader.chrsize;
        this.scrolltype = this.loader.scrolltype;
        this.savesram = this.loader.savesram;
        this.prg = this.loader.load(this.prgsize, this.prgoff);
        this.region = this.loader.tvtype;
        this.submapper = this.loader.submapper;
        this.crc = Mapper.crc32(this.prg);
        if (this.crc == 1092891794L || this.crc == 2563560325L) {
            this.hasprgram = false;
        }
        this.chr = this.loader.load(this.chrsize, this.chroff);
        if (this.chrsize == 0) {
            this.haschrram = true;
            this.chrsize = 8192;
            this.chr = new int[8192];
        }
        this.prg_map = new int[32];
        for (n = 0; n < 32; ++n) {
            this.prg_map[n] = 1024 * n & this.prgsize - 1;
        }
        this.chr_map = new int[8];
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * n & this.chrsize - 1;
        }
        this.cpuram = new CPURAM(this);
        this.cpu = new CPU(this.cpuram);
        this.ppu = new PPU(this);
        Arrays.fill(this.pput0, 160);
        Arrays.fill(this.pput1, 176);
        Arrays.fill(this.pput2, 192);
        Arrays.fill(this.pput3, 208);
        this.setmirroring(this.scrolltype);
    }

    public void reset() {
    }

    public void cartWrite(int n, int n2) {
        if (n >= 24576 && n < 32768) {
            this.prgram[n & 0x1FFF] = n2;
        }
    }

    public int cartRead(int n) {
        if (n >= 32768) {
            return this.prg[this.prg_map[(n & Short.MAX_VALUE) >> 10] + (n & 0x3FF)];
        }
        if (n >= 24576 && this.hasprgram) {
            return this.prgram[n & 0x1FFF];
        }
        return n >> 8;
    }

    public int ppuRead(int n) {
        if (n < 8192) {
            return this.chr[this.chr_map[n >> 10] + (n & 0x3FF)];
        }
        switch (n & 0xC00) {
            case 0: {
                return this.nt0[n & 0x3FF];
            }
            case 1024: {
                return this.nt1[n & 0x3FF];
            }
            case 2048: {
                return this.nt2[n & 0x3FF];
            }
        }
        if (n >= 16128) {
            if ((n &= 0x1F) >= 16 && (n & 3) == 0) {
                n -= 16;
            }
            return this.ppu.pal[n];
        }
        return this.nt3[n & 0x3FF];
    }

    public void ppuWrite(int n, int n2) {
        if ((n &= 0x3FFF) < 8192) {
            if (this.haschrram) {
                this.chr[this.chr_map[n >> 10] + (n & 0x3FF)] = n2;
            }
        } else {
            switch (n & 0xC00) {
                case 0: {
                    this.nt0[n & 0x3FF] = n2;
                    break;
                }
                case 1024: {
                    this.nt1[n & 0x3FF] = n2;
                    break;
                }
                case 2048: {
                    this.nt2[n & 0x3FF] = n2;
                    break;
                }
                case 3072: {
                    if (n >= 16128 && n <= 16383) {
                        if ((n &= 0x1F) >= 16 && (n & 3) == 0) {
                            n -= 16;
                        }
                        this.ppu.pal[n] = n2 & 0x3F;
                        break;
                    }
                    this.nt3[n & 0x3FF] = n2;
                    break;
                }
                default: {
                    System.err.println("where?");
                }
            }
        }
    }

    public void notifyscanline(int n) {
    }

    public void cpucycle(int n) {
    }

    public static Mapper getCorrectMapper(ROMLoader rOMLoader) throws BadMapperException {
        int n = rOMLoader.mappertype;
        boolean bl = rOMLoader.chrsize == 0;
        switch (n) {
            case -1: {
                return new NSFMapper();
            }
            case 0: {
                return new NromMapper();
            }
            case 1: {
                return new MMC1Mapper();
            }
            case 2: {
                return new UnromMapper();
            }
            case 3: {
                return new CnromMapper();
            }
            case 4: {
                return new MMC3Mapper();
            }
            case 5: {
                return new MMC5Mapper();
            }
            case 7: {
                return new AnromMapper();
            }
            case 9: {
                return new MMC2Mapper();
            }
            case 10: {
                return new MMC4Mapper();
            }
            case 11: {
                return new ColorDreamsMapper();
            }
            case 15: 
            case 169: {
                return new Mapper15();
            }
            case 19: {
                return new NamcoMapper();
            }
            case 21: 
            case 23: 
            case 25: {
                return new VRC4Mapper(n);
            }
            case 22: {
                return new VRC2Mapper();
            }
            case 24: 
            case 26: {
                return new VRC6Mapper(n);
            }
            case 31: {
                return new Mapper31();
            }
            case 33: {
                return new Mapper33();
            }
            case 34: {
                if (bl) {
                    return new BnromMapper();
                }
                return new NINA_001_Mapper();
            }
            case 36: {
                return new Mapper36();
            }
            case 38: {
                return new CrimeBustersMapper();
            }
            case 41: {
                return new CaltronMapper();
            }
            case 47: {
                return new Mapper47();
            }
            case 48: {
                return new Mapper48();
            }
            case 58: {
                return new Mapper58();
            }
            case 60: {
                return new Mapper60();
            }
            case 61: {
                return new Mapper61();
            }
            case 62: {
                return new Mapper62();
            }
            case 64: {
                return new TengenRamboMapper();
            }
            case 65: {
                return new IremH3001Mapper();
            }
            case 66: {
                return new GnromMapper();
            }
            case 67: {
                return new Sunsoft03Mapper();
            }
            case 68: {
                return new AfterburnerMapper();
            }
            case 69: {
                return new FME7Mapper();
            }
            case 70: {
                return new Mapper70();
            }
            case 71: {
                return new CodemastersMapper();
            }
            case 72: {
                return new Mapper72();
            }
            case 73: {
                return new VRC3Mapper();
            }
            case 75: {
                return new VRC1Mapper();
            }
            case 76: {
                return new Mapper76();
            }
            case 78: {
                return new Mapper78();
            }
            case 79: 
            case 113: {
                return new NINA_003_006_Mapper(n);
            }
            case 85: {
                return new VRC7Mapper();
            }
            case 86: {
                return new Mapper86();
            }
            case 87: {
                return new Mapper87();
            }
            case 88: 
            case 154: {
                return new Namcot34x3Mapper(n);
            }
            case 89: 
            case 93: {
                return new Sunsoft02Mapper(n);
            }
            case 92: {
                return new Mapper92();
            }
            case 94: {
                return new Mapper94();
            }
            case 97: {
                return new Mapper97();
            }
            case 107: {
                return new Mapper107();
            }
            case 112: {
                return new Mapper112();
            }
            case 119: {
                return new Mapper119();
            }
            case 140: {
                return new Mapper140();
            }
            case 152: {
                return new Mapper152();
            }
            case 180: {
                return new CrazyClimberMapper();
            }
            case 182: {
                return new Mapper182();
            }
            case 184: {
                return new Sunsoft01Mapper();
            }
            case 185: {
                return new Mapper185();
            }
            case 200: {
                return new Mapper200();
            }
            case 201: {
                return new Mapper201();
            }
            case 203: {
                return new Mapper203();
            }
            case 206: {
                return new MIMICMapper();
            }
            case 212: {
                return new Mapper212();
            }
            case 213: {
                return new Mapper213();
            }
            case 214: {
                return new Mapper214();
            }
            case 225: {
                return new Mapper225();
            }
            case 226: {
                return new Mapper226();
            }
            case 228: {
                return new Action52Mapper();
            }
            case 229: {
                return new Mapper229();
            }
            case 231: {
                return new Mapper231();
            }
            case 240: {
                return new Mapper240();
            }
            case 241: {
                return new Mapper241();
            }
            case 242: {
                return new Mapper242();
            }
            case 244: {
                return new Mapper244();
            }
            case 246: {
                return new Mapper246();
            }
            case 255: {
                return new Mapper255();
            }
        }
        System.err.println("unsupported mapper # " + n);
        throw new BadMapperException("Unsupported mapper: " + n);
    }

    public String getrominfo() {
        return "ROM INFO: \nFilename:     " + this.loader.name + "\nMapper:       " + this.mappertype + "\nPRG Size:     " + this.prgsize / 1024 + " K\nCHR Size:     " + (this.haschrram ? 0 : this.chrsize / 1024) + " K\nMirroring:    " + this.scrolltype.toString() + "\nBattery Save: " + (this.savesram ? "Yes" : "No") + "\nCRC:          " + utils.hex(this.crc);
    }

    public boolean hasSRAM() {
        return this.savesram;
    }

    public void setLoader(ROMLoader rOMLoader) {
        this.loader = rOMLoader;
    }

    public CPURAM getCPURAM() {
        return this.cpuram;
    }

    public void checkA12(int n) {
    }

    public void setPRGRAM(int[] nArray) {
        this.prgram = (int[])nArray.clone();
    }

    public int[] getPRGRam() {
        return (int[])this.prgram.clone();
    }

    public final void setmirroring(MirrorType mirrorType) {
        switch (mirrorType) {
            case H_MIRROR: {
                this.nt0 = this.pput0;
                this.nt1 = this.pput0;
                this.nt2 = this.pput1;
                this.nt3 = this.pput1;
                break;
            }
            case V_MIRROR: {
                this.nt0 = this.pput0;
                this.nt1 = this.pput1;
                this.nt2 = this.pput0;
                this.nt3 = this.pput1;
                break;
            }
            case SS_MIRROR0: {
                this.nt0 = this.pput0;
                this.nt1 = this.pput0;
                this.nt2 = this.pput0;
                this.nt3 = this.pput0;
                break;
            }
            case SS_MIRROR1: {
                this.nt0 = this.pput1;
                this.nt1 = this.pput1;
                this.nt2 = this.pput1;
                this.nt3 = this.pput1;
                break;
            }
            default: {
                this.nt0 = this.pput0;
                this.nt1 = this.pput1;
                this.nt2 = this.pput2;
                this.nt3 = this.pput3;
            }
        }
    }

    public TVType getTVType() {
        int n = this.prefs.getInt("region", 0);
        switch (n) {
            default: {
                return this.region;
            }
            case 1: {
                return TVType.NTSC;
            }
            case 2: {
                return TVType.PAL;
            }
            case 3: 
        }
        return TVType.DENDY;
    }

    public void init() {
    }

    public static enum MirrorType {
        H_MIRROR,
        V_MIRROR,
        SS_MIRROR0,
        SS_MIRROR1,
        FOUR_SCREEN_MIRROR;

    }

    public static enum TVType {
        NTSC,
        PAL,
        DENDY;

    }
}

