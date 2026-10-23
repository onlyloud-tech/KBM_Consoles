/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes;

import com.grapeshot.halfnes.FileUtils;
import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class ROMLoader {
    public String name;
    public int prgsize;
    public int chrsize;
    public Mapper.MirrorType scrolltype;
    public Mapper.TVType tvtype;
    public int mappertype;
    public int submapper;
    public int prgoff;
    public int chroff;
    public boolean savesram = false;
    public int[] header;
    private final int[] therom;

    public ROMLoader(String string) {
        this.therom = FileUtils.readfromfile(string);
        this.name = string;
    }

    private void ReadHeader(int n) {
        this.header = new int[n];
        System.arraycopy(this.therom, 0, this.header, 0, n);
    }

    public void parseHeader() throws BadMapperException {
        this.ReadHeader(16);
        if (this.header[0] == 78 && this.header[1] == 69 && this.header[2] == 83 && this.header[3] == 26) {
            this.scrolltype = (this.header[6] & 8) != 0 ? Mapper.MirrorType.FOUR_SCREEN_MIRROR : ((this.header[6] & 1) != 0 ? Mapper.MirrorType.V_MIRROR : Mapper.MirrorType.H_MIRROR);
            this.savesram = (this.header[6] & 2) != 0;
            this.mappertype = this.header[6] >> 4;
            if ((this.header[7] >> 2 & 3) == 2) {
                System.err.println("NES 2 format");
                this.mappertype += this.header[7] >> 4 << 4;
                this.mappertype += (this.header[8] & 0xF) << 8;
                this.submapper = this.header[8] >> 4;
                this.prgsize = Math.min(this.therom.length - 16, 16384 * (this.header[4] + ((this.header[9] & 0xF) << 8)));
                if (this.prgsize == 0) {
                    throw new BadMapperException("No PRG ROM size in header");
                }
                this.chrsize = Math.min(this.therom.length - 16 - this.prgsize, 8192 * (this.header[5] + (this.header[9] >> 4 << 8)));
                if ((this.header[12] & 3) == 1) {
                    this.tvtype = Mapper.TVType.PAL;
                    System.err.println("pal");
                } else {
                    this.tvtype = Mapper.TVType.NTSC;
                }
            } else {
                this.prgsize = Math.min(this.therom.length - 16, 16384 * this.header[4]);
                if (this.prgsize == 0) {
                    throw new BadMapperException("No PRG ROM size in header");
                }
                this.chrsize = Math.min(this.therom.length - 16 - this.prgsize, 8192 * this.header[5]);
                if (this.header[11] + this.header[12] + this.header[13] + this.header[14] + this.header[15] == 0) {
                    this.mappertype += this.header[7] >> 4 << 4;
                    if ((this.header[9] & 1) != 0) {
                        this.tvtype = Mapper.TVType.PAL;
                        System.err.println("pal header type 1");
                    } else if ((this.header[10] & 3) == 2) {
                        this.tvtype = Mapper.TVType.PAL;
                        System.err.println("pal header type 2");
                    } else {
                        this.tvtype = Mapper.TVType.NTSC;
                    }
                } else {
                    System.err.println("diskdude (please clean your roms)");
                    this.tvtype = Mapper.TVType.NTSC;
                }
            }
            this.prgoff = 0;
            this.chroff = 0 + this.prgsize;
        } else if (78 == this.header[0] && 69 == this.header[1] && 83 == this.header[2] && 77 == this.header[3] && 26 == this.header[4]) {
            this.mappertype = -1;
            this.ReadHeader(128);
            this.prgsize = this.therom.length - 128;
        } else {
            if (this.header[0] == 85) {
                throw new BadMapperException("This is a UNIF file with the wrong extension");
            }
            throw new BadMapperException("iNES Header Invalid");
        }
    }

    public int[] load(int n, int n2) {
        int[] nArray = new int[n];
        System.arraycopy(this.therom, n2 + this.header.length, nArray, 0, n);
        return nArray;
    }

    public int romlen() {
        return this.therom.length - this.header.length;
    }
}

