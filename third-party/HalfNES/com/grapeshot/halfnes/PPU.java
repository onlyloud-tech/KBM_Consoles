/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes;

import com.grapeshot.halfnes.PrefsSingleton;
import com.grapeshot.halfnes.mappers.Mapper;
import com.grapeshot.halfnes.ui.DebugUI;
import com.grapeshot.halfnes.ui.GUIInterface;
import com.grapeshot.halfnes.utils;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class PPU {
    public Mapper mapper;
    private int oamaddr;
    private int oamstart;
    private int readbuffer = 0;
    private int loopyV = 0;
    private int loopyT = 0;
    private int loopyX = 0;
    public int scanline = 0;
    public int cycles = 0;
    private int framecount = 0;
    private int div = 2;
    private final int[] OAM = new int[256];
    private final int[] secOAM = new int[32];
    private final int[] spriteshiftregH = new int[8];
    private final int[] spriteshiftregL = new int[8];
    private final int[] spriteXlatch = new int[8];
    private final int[] spritepals = new int[8];
    private final int[] bitmap = new int[61440];
    private int found;
    private int bgShiftRegH;
    private int bgShiftRegL;
    private int bgAttrShiftRegH;
    private int bgAttrShiftRegL;
    private final boolean[] spritebgflags = new boolean[8];
    private boolean even = true;
    private boolean bgpattern = true;
    private boolean sprpattern;
    private boolean spritesize;
    private boolean nmicontrol;
    private boolean grayscale;
    private boolean bgClip;
    private boolean spriteClip;
    private boolean bgOn;
    private boolean spritesOn;
    private boolean vblankflag;
    private boolean sprite0hit;
    private boolean spriteoverflow;
    private int emph;
    public final int[] pal;
    private DebugUI debuggui;
    private int vraminc = 1;
    private static final boolean PPUDEBUG = PrefsSingleton.get().getBoolean("ntView", false);
    private BufferedImage nametableView;
    private final int[] bgcolors = new int[256];
    private int openbus = 0;
    private int nextattr;
    private int linelowbits;
    private int linehighbits;
    private int penultimateattr;
    private int numscanlines;
    private int vblankline;
    private final int[] cpudivider = new int[]{3, 3, 3, 3, 3};
    private int tileAddr = 0;
    private int cpudividerctr = 0;
    boolean dotcrawl = true;
    private boolean sprite0here = false;

    public PPU(Mapper mapper) {
        this.pal = new int[]{9, 1, 0, 1, 0, 2, 2, 13, 8, 16, 8, 36, 0, 0, 4, 44, 9, 1, 52, 3, 0, 4, 0, 20, 8, 58, 0, 2, 0, 32, 44, 8};
        this.mapper = mapper;
        Arrays.fill(this.OAM, 255);
        if (PPUDEBUG) {
            this.nametableView = new BufferedImage(512, 480, 4);
            this.debuggui = new DebugUI(512, 480);
            this.debuggui.run();
        }
        this.setParameters();
    }

    final void setParameters() {
        switch (this.mapper.getTVType()) {
            default: {
                this.numscanlines = 262;
                this.vblankline = 241;
                this.cpudivider[0] = 3;
                break;
            }
            case PAL: {
                this.numscanlines = 312;
                this.vblankline = 241;
                this.cpudivider[0] = 4;
                break;
            }
            case DENDY: {
                this.numscanlines = 312;
                this.vblankline = 291;
                this.cpudivider[0] = 3;
            }
        }
    }

    public void runFrame() {
        for (int i = 0; i < this.numscanlines; ++i) {
            this.clockLine(i);
        }
    }

    public final int read(int n) {
        switch (n) {
            case 2: {
                this.even = true;
                if (this.scanline == 241 && this.cycles == 1) {
                    this.vblankflag = false;
                }
                this.openbus = (this.vblankflag ? 128 : 0) | (this.sprite0hit ? 64 : 0) | (this.spriteoverflow ? 32 : 0) | this.openbus & 0x1F;
                this.vblankflag = false;
                break;
            }
            case 4: {
                this.openbus = this.OAM[this.oamaddr];
                if (!this.renderingOn() || this.scanline > 240) break;
                if (this.cycles < 64) {
                    return 255;
                }
                if (this.cycles <= 256) {
                    return 0;
                }
                if (this.cycles < 320) {
                    return 255;
                }
                return this.secOAM[0];
            }
            case 7: {
                int n2;
                if ((this.loopyV & 0x3FFF) < 16128) {
                    n2 = this.readbuffer;
                    this.readbuffer = this.mapper.ppuRead(this.loopyV & 0x3FFF);
                } else {
                    this.readbuffer = this.mapper.ppuRead((this.loopyV & 0x3FFF) - 4096);
                    n2 = this.mapper.ppuRead(this.loopyV);
                }
                if (!this.renderingOn() || this.scanline > 240 && this.scanline < this.numscanlines - 1) {
                    this.loopyV += this.vraminc;
                } else {
                    this.incLoopyVHoriz();
                    this.incLoopyVVert();
                }
                this.openbus = n2;
                break;
            }
            default: {
                return this.openbus;
            }
        }
        return this.openbus;
    }

    public final void write(int n, int n2) {
        this.openbus = n2;
        block0 : switch (n) {
            case 0: {
                this.loopyT &= 0xFFFFF3FF;
                this.loopyT |= (n2 & 3) << 10;
                this.vraminc = (n2 & 4) != 0 ? 32 : 1;
                this.sprpattern = (n2 & 8) != 0;
                this.bgpattern = (n2 & 0x10) != 0;
                this.spritesize = (n2 & 0x20) != 0;
                this.nmicontrol = (n2 & 0x80) != 0;
                break;
            }
            case 1: {
                this.grayscale = (n2 & 1) != 0;
                this.bgClip = (n2 & 2) == 0;
                this.spriteClip = (n2 & 4) == 0;
                this.bgOn = (n2 & 8) != 0;
                this.spritesOn = (n2 & 0x10) != 0;
                this.emph = (n2 & 0xE0) << 1;
                if (this.numscanlines != 312) break;
                int n3 = this.emph >> 6 & 1;
                int n4 = this.emph >> 7 & 1;
                this.emph &= 0xF3F;
                this.emph |= n3 << 7 | n4 << 6;
                break;
            }
            case 3: {
                this.oamaddr = n2 & 0xFF;
                break;
            }
            case 4: {
                this.OAM[this.oamaddr++] = (this.oamaddr & 3) == 2 ? n2 & 0xE3 : n2;
                this.oamaddr &= 0xFF;
                break;
            }
            case 5: {
                if (this.even) {
                    this.loopyT &= 0xFFFFFFE0;
                    this.loopyX = n2 & 7;
                    this.loopyT |= n2 >> 3;
                    this.even = false;
                    break;
                }
                this.loopyT &= 0xFFFF8FFF;
                this.loopyT |= (n2 & 7) << 12;
                this.loopyT &= 0xFFFFFC1F;
                this.loopyT |= (n2 & 0xF8) << 2;
                this.even = true;
                break;
            }
            case 6: {
                if (this.even) {
                    this.loopyT &= 0xC0FF;
                    this.loopyT |= (n2 & 0x3F) << 8;
                    this.loopyT &= 0x3FFF;
                    this.even = false;
                    break;
                }
                this.loopyT &= 0xFFF00;
                this.loopyT |= n2;
                this.loopyV = this.loopyT;
                this.even = true;
                break;
            }
            case 7: {
                this.mapper.ppuWrite(this.loopyV & 0x3FFF, n2);
                if (!this.renderingOn() || this.scanline > 240 && this.scanline < this.numscanlines - 1) {
                    this.loopyV += this.vraminc;
                    break;
                }
                if ((this.loopyV & 0x7000) == 28672) {
                    int n5 = this.loopyV & 0x3E0;
                    this.loopyV &= 0xFFF;
                    switch (n5) {
                        case 928: {
                            this.loopyV ^= 0xBA0;
                            break block0;
                        }
                        case 992: {
                            this.loopyV ^= 0x3E0;
                            break block0;
                        }
                    }
                    this.loopyV += 32;
                    break;
                }
                this.loopyV += 4096;
                break;
            }
        }
    }

    public boolean renderingOn() {
        return this.bgOn || this.spritesOn;
    }

    public final boolean mmc3CounterClocking() {
        return this.bgpattern != this.sprpattern && this.renderingOn();
    }

    public final void clockLine(int n) {
        int n2;
        this.cycles = n2 = this.numscanlines == 262 && n == 0 && this.renderingOn() && (this.framecount & 2) == 0 ? 1 : 0;
        while (this.cycles < 341) {
            this.clock();
            ++this.cycles;
        }
    }

    public final void clock() {
        if (this.cycles == 1) {
            if (this.scanline == 0) {
                this.dotcrawl = this.renderingOn();
            }
            if (this.scanline < 240) {
                this.bgcolors[this.scanline] = this.pal[0];
            }
        }
        if (this.scanline < 240 || this.scanline == this.numscanlines - 1) {
            if (this.renderingOn() && (this.cycles >= 1 && this.cycles <= 256 || this.cycles >= 321 && this.cycles <= 336)) {
                this.bgFetch();
            } else if (this.cycles == 257 && this.renderingOn()) {
                this.loopyV &= 0xFFFFFBE0;
                this.loopyV |= this.loopyT & 0x41F;
            } else if (this.cycles > 257 && this.cycles <= 341) {
                this.oamaddr = 0;
            }
            if (this.cycles == 340 && this.renderingOn()) {
                this.fetchNTByte();
                this.fetchNTByte();
            }
            if (this.cycles == 65 && this.renderingOn()) {
                this.oamstart = this.oamaddr;
            }
            if (this.cycles == 260 && this.renderingOn()) {
                this.evalSprites();
            }
            if (this.scanline == this.numscanlines - 1) {
                if (this.cycles == 0) {
                    this.vblankflag = false;
                    this.sprite0hit = false;
                    this.spriteoverflow = false;
                } else if (this.cycles >= 280 && this.cycles <= 304 && this.renderingOn()) {
                    this.loopyV = this.loopyT;
                }
            }
        } else if (this.scanline == this.vblankline && this.cycles == 1) {
            this.vblankflag = true;
        }
        if (!this.renderingOn() || this.scanline > 240 && this.scanline < this.numscanlines - 1) {
            this.mapper.checkA12(this.loopyV & 0x3FFF);
        }
        if (this.scanline < 240 && this.cycles >= 1 && this.cycles <= 256) {
            int n = (this.scanline << 8) + (this.cycles - 1);
            if (this.bgOn) {
                boolean bl = this.drawBGPixel(n);
                this.drawSprites(this.scanline, this.cycles - 1, bl);
            } else if (this.spritesOn) {
                int n2;
                this.bitmap[n] = n2 = this.loopyV > 16128 && this.loopyV < 16383 ? this.mapper.ppuRead(this.loopyV) : this.pal[0];
                this.drawSprites(this.scanline, this.cycles - 1, true);
            } else {
                int n3;
                this.bitmap[n] = n3 = this.loopyV > 16128 && this.loopyV < 16383 ? this.mapper.ppuRead(this.loopyV) : this.pal[0];
            }
            if (this.grayscale) {
                int n4 = n;
                this.bitmap[n4] = this.bitmap[n4] & 0x30;
            }
            this.bitmap[n] = this.bitmap[n] & 0x3F | this.emph;
        }
        if (this.vblankflag && this.nmicontrol) {
            this.mapper.cpu.setNMI(true);
        } else {
            this.mapper.cpu.setNMI(false);
        }
        this.div = (this.div + 1) % this.cpudivider[this.cpudividerctr];
        if (this.div == 0) {
            this.mapper.cpu.runcycle(this.scanline, this.cycles);
            this.mapper.cpucycle(1);
            this.cpudividerctr = (this.cpudividerctr + 1) % this.cpudivider.length;
        }
        if (this.cycles == 257) {
            this.mapper.notifyscanline(this.scanline);
        } else if (this.cycles == 340) {
            this.scanline = (this.scanline + 1) % this.numscanlines;
            if (this.scanline == 0) {
                ++this.framecount;
            }
        }
    }

    private void bgFetch() {
        this.bgAttrShiftRegH |= this.nextattr >> 1 & 1;
        this.bgAttrShiftRegL |= this.nextattr & 1;
        switch (this.cycles - 1 & 7) {
            case 1: {
                this.fetchNTByte();
                break;
            }
            case 3: {
                this.penultimateattr = this.getAttribute((this.loopyV & 0xC00) + 9152, this.loopyV & 0x1F, (this.loopyV & 0x3E0) >> 5);
                break;
            }
            case 5: {
                this.linelowbits = this.mapper.ppuRead(this.tileAddr + ((this.loopyV & 0x7000) >> 12));
                break;
            }
            case 7: {
                this.linehighbits = this.mapper.ppuRead(this.tileAddr + 8 + ((this.loopyV & 0x7000) >> 12));
                this.bgShiftRegL |= this.linelowbits;
                this.bgShiftRegH |= this.linehighbits;
                this.nextattr = this.penultimateattr;
                if (this.cycles != 256) {
                    this.incLoopyVHoriz();
                    break;
                }
                this.incLoopyVVert();
                break;
            }
        }
        if (this.cycles >= 321 && this.cycles <= 336) {
            this.bgShiftClock();
        }
    }

    private void incLoopyVVert() {
        if ((this.loopyV & 0x7000) == 28672) {
            this.loopyV &= 0xFFFF8FFF;
            int n = (this.loopyV & 0x3E0) >> 5;
            if (n == 29) {
                n = 0;
                this.loopyV ^= 0x800;
            } else {
                n = n + 1 & 0x1F;
            }
            this.loopyV = this.loopyV & 0xFFFFFC1F | n << 5;
        } else {
            this.loopyV += 4096;
        }
    }

    private void incLoopyVHoriz() {
        if ((this.loopyV & 0x1F) == 31) {
            this.loopyV &= 0xFFFFFFE0;
            this.loopyV ^= 0x400;
        } else {
            ++this.loopyV;
        }
    }

    private void fetchNTByte() {
        this.tileAddr = this.mapper.ppuRead((this.loopyV & 0xC00 | 0x2000) + (this.loopyV & 0x3FF)) * 16 + (this.bgpattern ? 4096 : 0);
    }

    private boolean drawBGPixel(int n) {
        boolean bl;
        if (this.bgClip && (n & 0xFF) < 8) {
            this.bitmap[n] = this.pal[0];
            bl = true;
        } else {
            int n2 = ((this.bgShiftRegH >> -this.loopyX + 16 & 1) << 1) + (this.bgShiftRegL >> -this.loopyX + 16 & 1);
            int n3 = ((this.bgAttrShiftRegH >> -this.loopyX + 8 & 1) << 1) + (this.bgAttrShiftRegL >> -this.loopyX + 8 & 1);
            bl = n2 == 0;
            this.bitmap[n] = bl ? this.pal[0] : this.pal[(n3 << 2) + n2];
        }
        this.bgShiftClock();
        return bl;
    }

    private void bgShiftClock() {
        this.bgShiftRegH <<= 1;
        this.bgShiftRegL <<= 1;
        this.bgAttrShiftRegH <<= 1;
        this.bgAttrShiftRegL <<= 1;
    }

    private void evalSprites() {
        int n;
        this.sprite0here = false;
        this.found = 0;
        Arrays.fill(this.secOAM, 255);
        for (n = this.oamstart; n < 255; n += 4) {
            int n2 = this.OAM[n];
            int n3 = this.scanline - n2;
            if (n2 > this.scanline || n3 > (this.spritesize ? 15 : 7)) continue;
            if (n == 0) {
                this.sprite0here = true;
            }
            if (this.found >= 8) {
                this.spriteoverflow = true;
                break;
            }
            this.secOAM[this.found * 4] = this.OAM[n];
            int n4 = this.OAM[n + 2];
            this.spritebgflags[this.found] = (n4 & 0x20) != 0;
            this.spriteXlatch[this.found] = this.OAM[n + 3];
            this.spritepals[this.found] = ((n4 & 3) + 4) * 4;
            if ((n4 & 0x80) != 0) {
                n3 = (this.spritesize ? 15 : 7) - n3;
            }
            if (n3 > 7) {
                n3 += 8;
            }
            int n5 = this.OAM[n + 1];
            this.spriteFetch(this.spritesize, n5, n3, n4);
            ++this.found;
        }
        for (n = this.found; n < 8; ++n) {
            this.spriteshiftregL[this.found] = 0;
            this.spriteshiftregH[this.found] = 0;
            this.spriteFetch(this.spritesize, 255, 0, 0);
        }
    }

    private void spriteFetch(boolean bl, int n, int n2, int n3) {
        boolean bl2;
        int n4 = bl ? (n & 1) * 4096 + (n & 0xFE) * 16 : n * 16 + (this.sprpattern ? 4096 : 0);
        n4 += n2;
        boolean bl3 = bl2 = (n3 & 0x40) != 0;
        if (!bl2) {
            this.spriteshiftregL[this.found] = utils.reverseByte(this.mapper.ppuRead(n4));
            this.spriteshiftregH[this.found] = utils.reverseByte(this.mapper.ppuRead(n4 + 8));
        } else {
            this.spriteshiftregL[this.found] = this.mapper.ppuRead(n4);
            this.spriteshiftregH[this.found] = this.mapper.ppuRead(n4 + 8);
        }
    }

    private void drawSprites(int n, int n2, boolean bl) {
        int n3 = !this.spriteClip ? 0 : 8;
        int n4 = 0;
        int n5 = 7;
        for (int i = this.found - 1; i >= 0; --i) {
            int n6 = n2 - this.spriteXlatch[i];
            if (n6 < 0 || n6 > 8) continue;
            if ((this.spriteshiftregH[i] & 1) + (this.spriteshiftregL[i] & 1) != 0) {
                n5 = i;
                n4 = 2 * (this.spriteshiftregH[i] & 1) + (this.spriteshiftregL[i] & 1);
            }
            int n7 = i;
            this.spriteshiftregH[n7] = this.spriteshiftregH[n7] >> 1;
            int n8 = i;
            this.spriteshiftregL[n8] = this.spriteshiftregL[n8] >> 1;
        }
        if (n4 == 0 || n2 < n3 || !this.spritesOn) {
            return;
        }
        if (this.sprite0here && n5 == 0 && !bl && n2 < 255) {
            this.sprite0hit = true;
        }
        if (!this.spritebgflags[n5] || bl) {
            this.bitmap[(n << 8) + n2] = this.pal[this.spritepals[n5] + n4];
        }
    }

    private int getAttribute(int n, int n2, int n3) {
        int n4 = this.mapper.ppuRead(n + (n2 >> 2) + 8 * (n3 >> 2));
        if ((n3 & 2) != 0) {
            if ((n2 & 2) != 0) {
                return n4 >> 6 & 3;
            }
            return n4 >> 4 & 3;
        }
        if ((n2 & 2) != 0) {
            return n4 >> 2 & 3;
        }
        return n4 & 3;
    }

    private void debugDraw() {
        int n;
        int n2;
        for (n2 = 0; n2 < 32; ++n2) {
            for (n = 0; n < 30; ++n) {
                this.nametableView.setRGB(n2 * 8, n * 8, 8, 8, this.debugGetTile(this.mapper.ppuRead(8192 + n2 + 32 * n) * 16 + (this.bgpattern ? 4096 : 0)), 0, 8);
            }
        }
        for (n2 = 0; n2 < 32; ++n2) {
            for (n = 0; n < 30; ++n) {
                this.nametableView.setRGB(n2 * 8 + 255, n * 8, 8, 8, this.debugGetTile(this.mapper.ppuRead(9216 + n2 + 32 * n) * 16 + (this.bgpattern ? 4096 : 0)), 0, 8);
            }
        }
        for (n2 = 0; n2 < 32; ++n2) {
            for (n = 0; n < 30; ++n) {
                this.nametableView.setRGB(n2 * 8, n * 8 + 239, 8, 8, this.debugGetTile(this.mapper.ppuRead(10240 + n2 + 32 * n) * 16 + (this.bgpattern ? 4096 : 0)), 0, 8);
            }
        }
        for (n2 = 0; n2 < 32; ++n2) {
            for (n = 0; n < 30; ++n) {
                this.nametableView.setRGB(n2 * 8 + 255, n * 8 + 239, 8, 8, this.debugGetTile(this.mapper.ppuRead(11264 + n2 + 32 * n) * 16 + (this.bgpattern ? 4096 : 0)), 0, 8);
            }
        }
        this.debuggui.setFrame(this.nametableView);
    }

    private int[] debugGetTile(int n) {
        int[] nArray = new int[64];
        for (int i = 0; i < 8; ++i) {
            for (int j = 0; j < 8; ++j) {
                nArray[8 * i + j] = ((this.mapper.ppuRead(i + n) & 128 - j) != 0 ? 0x555555 : 0) + ((this.mapper.ppuRead(i + n + 8) & 128 - j) != 0 ? 0xAAAAAA : 0);
            }
        }
        return nArray;
    }

    public final void renderFrame(GUIInterface gUIInterface) {
        if (PPUDEBUG) {
            this.debugDraw();
        }
        if (gUIInterface != null) {
            gUIInterface.setFrame(this.bitmap, this.bgcolors, this.dotcrawl);
        }
    }
}

