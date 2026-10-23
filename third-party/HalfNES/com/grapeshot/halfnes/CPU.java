/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes;

import com.grapeshot.halfnes.CPURAM;
import com.grapeshot.halfnes.utils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class CPU {
    private final CPURAM ram;
    private int cycles;
    public int clocks;
    private int A;
    private int X;
    private int Y;
    private int S;
    public int PC;
    private boolean carryFlag = false;
    private boolean zeroFlag = false;
    private boolean interruptsDisabled = true;
    private boolean decimalModeFlag = false;
    private boolean overflowFlag = false;
    private boolean negativeFlag = false;
    private boolean previntflag = false;
    private boolean nmi = false;
    private boolean prevnmi = false;
    private boolean logging = false;
    private int pb = 0;
    public int interrupt = 0;
    public boolean nmiNext = false;
    public boolean idle = false;
    private static final boolean decimalModeEnable = false;
    private static final boolean idleLoopSkip = true;
    private boolean interruptDelay = false;
    private static final String[] opcodes = CPU.opcodes();
    private static final boolean battletoadsHackOn = true;
    private boolean dirtyBattletoadsHack = false;
    private int hackAddr = 0;
    private int hackData = 0;
    OutputStreamWriter w;

    public CPU(CPURAM cPURAM) {
        this.ram = cPURAM;
        if (this.logging) {
            this.startLog();
        }
    }

    public void startLog() {
        this.logging = true;
        try {
            this.w = new OutputStreamWriter((OutputStream)new FileOutputStream(new File("nesdebug.txt")), StandardCharsets.UTF_8);
        }
        catch (IOException iOException) {
            System.err.println("Cannot create debug log" + iOException.getLocalizedMessage());
        }
    }

    public void startLog(String string) {
        this.logging = true;
        try {
            this.w = new OutputStreamWriter((OutputStream)new FileOutputStream(new File(string)), StandardCharsets.UTF_8);
        }
        catch (IOException iOException) {
            System.err.println("Cannot create debug log" + iOException.getLocalizedMessage());
        }
    }

    public void stopLog() {
        this.logging = false;
        this.flushLog();
    }

    public void init() {
        this.init(null);
    }

    public void init(Integer n) {
        int n2;
        for (n2 = 0; n2 < 2048; ++n2) {
            this.ram.write(n2, 255);
        }
        this.ram.write(8, 247);
        this.ram.write(9, 239);
        this.ram.write(10, 223);
        this.ram.write(15, 191);
        for (n2 = 16384; n2 <= 16399; ++n2) {
            this.ram.write(n2, 0);
        }
        this.ram.write(16405, 0);
        this.ram.write(16407, 0);
        this.A = 0;
        this.X = 0;
        this.Y = 0;
        this.S = 253;
        this.PC = n == null ? this.ram.read(65533) * 256 + this.ram.read(65532) : n;
    }

    public void reset() {
        this.PC = this.ram.read(65533) * 256 + this.ram.read(65532);
        this.ram.write(16405, 0);
        this.ram.write(16407, this.ram.read(16407));
        this.S -= 3;
        this.S &= 0xFF;
        this.interruptsDisabled = true;
    }

    public void modcycles() {
        this.clocks = 0;
    }

    public void stealcycles(int n) {
        this.cycles += n;
        this.log("**STEAL " + n + "**");
    }

    public final void runcycle(int n, int n2) {
        this.ram.read(16384);
        ++this.clocks;
        if (this.ram.apu.sprdma_count > 0) {
            --this.ram.apu.sprdma_count;
            if (this.ram.apu.sprdma_count == 0) {
                this.cycles += 513;
            }
        }
        if (this.dirtyBattletoadsHack && this.cycles == 1) {
            this.ram.write(this.hackAddr, this.hackData);
            this.dirtyBattletoadsHack = false;
        }
        if (this.cycles-- > 0) {
            return;
        }
        if (this.nmiNext) {
            this.nmi();
            this.nmiNext = false;
        }
        if (this.nmi && !this.prevnmi) {
            this.nmiNext = true;
        }
        this.prevnmi = this.nmi;
        if (this.interrupt > 0) {
            if (!this.interruptsDisabled && !this.interruptDelay) {
                this.interrupt();
                this.cycles += 7;
                return;
            }
            if (this.interruptDelay) {
                this.interruptDelay = false;
                if (!this.previntflag) {
                    this.interrupt();
                    this.cycles += 7;
                    return;
                }
            }
        } else {
            this.interruptDelay = false;
        }
        if (this.idle) {
            this.cycles += 3;
            return;
        }
        this.pb = 0;
        int n3 = this.ram.read(this.PC++);
        if (this.logging) {
            String string = String.format(opcodes[n3], this.ram.read(this.PC), this.ram.read(this.PC + 1), this.PC + (byte)this.ram.read(this.PC) + 1);
            this.log(utils.hex(this.PC - 1) + " " + utils.hex(n3) + String.format(" %-14s ", string) + this.status() + " CYC:" + n2 + " SL:" + n + "\n");
        }
        if (this.cycles == 0) {
            this.flushLog();
        }
        switch (n3) {
            case 105: {
                this.adc(this.imm());
                this.cycles += 2;
                break;
            }
            case 101: {
                this.adc(this.zpg());
                this.cycles += 3;
                break;
            }
            case 117: {
                this.adc(this.zpg(this.X));
                this.cycles += 4;
                break;
            }
            case 109: {
                this.adc(this.abs());
                this.cycles += 4;
                break;
            }
            case 125: {
                this.adc(this.abs(this.X, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 121: {
                this.adc(this.abs(this.Y, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 97: {
                this.adc(this.indX());
                this.cycles += 6;
                break;
            }
            case 113: {
                this.adc(this.indY(dummy.ONCARRY));
                this.cycles += 5 + this.pb;
                break;
            }
            case 147: {
                this.ahx(this.indY(dummy.ALWAYS));
                this.cycles += 6;
                break;
            }
            case 159: {
                this.ahx(this.abs(this.Y, dummy.ALWAYS));
                this.cycles += 5;
                break;
            }
            case 75: {
                this.alr(this.imm());
                this.cycles += 2;
                break;
            }
            case 11: {
                this.anc(this.imm());
                this.cycles += 2;
                break;
            }
            case 43: {
                this.anc(this.imm());
                this.cycles += 2;
                break;
            }
            case 41: {
                this.and(this.imm());
                this.cycles += 2;
                break;
            }
            case 37: {
                this.and(this.zpg());
                this.cycles += 3;
                break;
            }
            case 53: {
                this.and(this.zpg(this.X));
                this.cycles += 4;
                break;
            }
            case 45: {
                this.and(this.abs());
                this.cycles += 4;
                break;
            }
            case 61: {
                this.and(this.abs(this.X, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 57: {
                this.and(this.abs(this.Y, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 33: {
                this.and(this.indX());
                this.cycles += 6;
                break;
            }
            case 49: {
                this.and(this.indY(dummy.ONCARRY));
                this.cycles += 5 + this.pb;
                break;
            }
            case 107: {
                this.arr(this.imm());
                this.cycles += 2;
                break;
            }
            case 10: {
                this.aslA();
                this.cycles += 2;
                break;
            }
            case 6: {
                this.asl(this.zpg());
                this.cycles += 5;
                break;
            }
            case 22: {
                this.asl(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 14: {
                this.asl(this.abs());
                this.cycles += 6;
                break;
            }
            case 30: {
                this.asl(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 203: {
                this.axs(this.imm());
                this.cycles += 2;
                break;
            }
            case 36: {
                this.bit(this.zpg());
                this.cycles += 3;
                break;
            }
            case 44: {
                this.bit(this.abs());
                this.cycles += 4;
                break;
            }
            case 16: {
                this.branch(!this.negativeFlag);
                this.cycles += 2 + this.pb;
                break;
            }
            case 48: {
                this.branch(this.negativeFlag);
                this.cycles += 2 + this.pb;
                break;
            }
            case 80: {
                this.branch(!this.overflowFlag);
                this.cycles += 2 + this.pb;
                break;
            }
            case 112: {
                this.branch(this.overflowFlag);
                this.cycles += 2 + this.pb;
                break;
            }
            case 144: {
                this.branch(!this.carryFlag);
                this.cycles += 2 + this.pb;
                break;
            }
            case 176: {
                this.branch(this.carryFlag);
                this.cycles += 2 + this.pb;
                break;
            }
            case 208: {
                this.branch(!this.zeroFlag);
                this.cycles += 2 + this.pb;
                break;
            }
            case 240: {
                this.branch(this.zeroFlag);
                this.cycles += 2 + this.pb;
                break;
            }
            case 0: {
                this.breakinterrupt();
                this.cycles += 7;
                break;
            }
            case 201: {
                this.cmp(this.A, this.imm());
                this.cycles += 2;
                break;
            }
            case 197: {
                this.cmp(this.A, this.zpg());
                this.cycles += 3;
                break;
            }
            case 213: {
                this.cmp(this.A, this.zpg(this.X));
                this.cycles += 4;
                break;
            }
            case 205: {
                this.cmp(this.A, this.abs());
                this.cycles += 4;
                break;
            }
            case 221: {
                this.cmp(this.A, this.abs(this.X, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 217: {
                this.cmp(this.A, this.abs(this.Y, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 193: {
                this.cmp(this.A, this.indX());
                this.cycles += 6;
                break;
            }
            case 209: {
                this.cmp(this.A, this.indY(dummy.ONCARRY));
                this.cycles += 5 + this.pb;
                break;
            }
            case 224: {
                this.cmp(this.X, this.imm());
                this.cycles += 2;
                break;
            }
            case 228: {
                this.cmp(this.X, this.zpg());
                this.cycles += 3;
                break;
            }
            case 236: {
                this.cmp(this.X, this.abs());
                this.cycles += 4;
                break;
            }
            case 192: {
                this.cmp(this.Y, this.imm());
                this.cycles += 2;
                break;
            }
            case 196: {
                this.cmp(this.Y, this.zpg());
                this.cycles += 3;
                break;
            }
            case 204: {
                this.cmp(this.Y, this.abs());
                this.cycles += 4;
                break;
            }
            case 198: {
                this.dec(this.zpg());
                this.cycles += 5;
                break;
            }
            case 214: {
                this.dec(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 206: {
                this.dec(this.abs());
                this.cycles += 6;
                break;
            }
            case 222: {
                this.dec(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 195: {
                this.dcp(this.A, this.indX());
                this.cycles += 8;
                break;
            }
            case 211: {
                this.dcp(this.A, this.indY(dummy.ALWAYS));
                this.cycles += 8;
                break;
            }
            case 199: {
                this.dcp(this.A, this.zpg());
                this.cycles += 5;
                break;
            }
            case 215: {
                this.dcp(this.A, this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 219: {
                this.dcp(this.A, this.abs(this.Y, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 207: {
                this.dcp(this.A, this.abs());
                this.cycles += 6;
                break;
            }
            case 223: {
                this.dcp(this.A, this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 73: {
                this.eor(this.imm());
                this.cycles += 2;
                break;
            }
            case 69: {
                this.eor(this.zpg());
                this.cycles += 3;
                break;
            }
            case 85: {
                this.eor(this.zpg(this.X));
                this.cycles += 4;
                break;
            }
            case 77: {
                this.eor(this.abs());
                this.cycles += 4;
                break;
            }
            case 93: {
                this.eor(this.abs(this.X, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 89: {
                this.eor(this.abs(this.Y, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 65: {
                this.eor(this.indX());
                this.cycles += 6;
                break;
            }
            case 81: {
                this.eor(this.indY(dummy.ONCARRY));
                this.cycles += 5 + this.pb;
                break;
            }
            case 24: {
                this.carryFlag = false;
                this.cycles += 2;
                break;
            }
            case 56: {
                this.carryFlag = true;
                this.cycles += 2;
                break;
            }
            case 88: {
                this.delayInterrupt();
                this.interruptsDisabled = false;
                this.cycles += 2;
                break;
            }
            case 120: {
                this.delayInterrupt();
                this.interruptsDisabled = true;
                this.cycles += 2;
                break;
            }
            case 184: {
                this.overflowFlag = false;
                this.cycles += 2;
                break;
            }
            case 216: {
                this.decimalModeFlag = false;
                this.cycles += 2;
                break;
            }
            case 248: {
                this.decimalModeFlag = true;
                this.cycles += 2;
                break;
            }
            case 230: {
                this.inc(this.zpg());
                this.cycles += 5;
                break;
            }
            case 246: {
                this.inc(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 238: {
                this.inc(this.abs());
                this.cycles += 6;
                break;
            }
            case 254: {
                this.inc(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 227: {
                this.isc(this.indX());
                this.cycles += 8;
                break;
            }
            case 243: {
                this.isc(this.indY(dummy.ALWAYS));
                this.cycles += 8;
                break;
            }
            case 231: {
                this.isc(this.zpg());
                this.cycles += 5;
                break;
            }
            case 247: {
                this.isc(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 251: {
                this.isc(this.abs(this.Y, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 239: {
                this.isc(this.abs());
                this.cycles += 6;
                break;
            }
            case 255: {
                this.isc(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 76: {
                int n4 = this.PC;
                this.PC = this.abs();
                if (this.PC == n4 - 1) {
                    this.idle = true;
                }
                this.cycles += 3;
                break;
            }
            case 108: {
                int n5 = this.PC;
                this.PC = this.ind();
                if (this.PC == n5 - 1) {
                    this.idle = true;
                }
                this.cycles += 5;
                break;
            }
            case 32: {
                this.jsr(this.abs());
                this.cycles += 6;
                break;
            }
            case 2: 
            case 18: 
            case 34: 
            case 50: 
            case 66: 
            case 82: 
            case 98: 
            case 114: 
            case 146: 
            case 178: 
            case 210: 
            case 242: {
                System.err.println("KIL - CPU locked");
                this.flushLog();
                this.ram.apu.nes.runEmulation = false;
                break;
            }
            case 187: {
                this.las(this.abs(this.Y, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 163: {
                this.lax(this.indX());
                this.cycles += 6;
                break;
            }
            case 179: {
                this.lax(this.indY(dummy.ONCARRY));
                this.cycles += 5 + this.pb;
                break;
            }
            case 167: {
                this.lax(this.zpg());
                this.cycles += 3;
                break;
            }
            case 183: {
                this.lax(this.zpg(this.Y));
                this.cycles += 4;
                break;
            }
            case 171: {
                this.lax(this.imm());
                this.cycles += 2;
                break;
            }
            case 175: {
                this.lax(this.abs());
                this.cycles += 4;
                break;
            }
            case 191: {
                this.lax(this.abs(this.Y, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 169: {
                this.lda(this.imm());
                this.cycles += 2;
                break;
            }
            case 165: {
                this.lda(this.zpg());
                this.cycles += 3;
                break;
            }
            case 181: {
                this.lda(this.zpg(this.X));
                this.cycles += 4;
                break;
            }
            case 173: {
                this.lda(this.abs());
                this.cycles += 4;
                break;
            }
            case 189: {
                this.lda(this.abs(this.X, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 185: {
                this.lda(this.abs(this.Y, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 161: {
                this.lda(this.indX());
                this.cycles += 6;
                break;
            }
            case 177: {
                this.lda(this.indY(dummy.ONCARRY));
                this.cycles += 5 + this.pb;
                break;
            }
            case 162: {
                this.ldx(this.imm());
                this.cycles += 2;
                break;
            }
            case 166: {
                this.ldx(this.zpg());
                this.cycles += 3;
                break;
            }
            case 182: {
                this.ldx(this.zpg(this.Y));
                this.cycles += 4;
                break;
            }
            case 174: {
                this.ldx(this.abs());
                this.cycles += 4;
                break;
            }
            case 190: {
                this.ldx(this.abs(this.Y, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 160: {
                this.ldy(this.imm());
                this.cycles += 2;
                break;
            }
            case 164: {
                this.ldy(this.zpg());
                this.cycles += 3;
                break;
            }
            case 180: {
                this.ldy(this.zpg(this.X));
                this.cycles += 4;
                break;
            }
            case 172: {
                this.ldy(this.abs());
                this.cycles += 4;
                break;
            }
            case 188: {
                this.ldy(this.abs(this.X, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 74: {
                this.lsrA();
                this.cycles += 2;
                break;
            }
            case 70: {
                this.lsr(this.zpg());
                this.cycles += 5;
                break;
            }
            case 86: {
                this.lsr(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 78: {
                this.lsr(this.abs());
                this.cycles += 6;
                break;
            }
            case 94: {
                this.lsr(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 26: 
            case 58: 
            case 90: 
            case 122: 
            case 218: 
            case 234: 
            case 250: {
                this.cycles += 2;
                break;
            }
            case 128: 
            case 130: 
            case 137: 
            case 194: 
            case 226: {
                this.imm();
                this.cycles += 2;
                break;
            }
            case 4: 
            case 68: 
            case 100: {
                this.zpg();
                this.cycles += 3;
                break;
            }
            case 20: 
            case 52: 
            case 84: 
            case 116: 
            case 212: 
            case 244: {
                this.zpg(this.X);
                this.cycles += 4;
                break;
            }
            case 12: {
                this.abs();
                this.cycles += 4;
                break;
            }
            case 28: 
            case 60: 
            case 92: 
            case 124: 
            case 220: 
            case 252: {
                this.abs(this.X, dummy.ONCARRY);
                this.cycles += 4 + this.pb;
                break;
            }
            case 9: {
                this.ora(this.imm());
                this.cycles += 2;
                break;
            }
            case 5: {
                this.ora(this.zpg());
                this.cycles += 3;
                break;
            }
            case 21: {
                this.ora(this.zpg(this.X));
                this.cycles += 4;
                break;
            }
            case 13: {
                this.ora(this.abs());
                this.cycles += 4;
                break;
            }
            case 29: {
                this.ora(this.abs(this.X, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 25: {
                this.ora(this.abs(this.Y, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 1: {
                this.ora(this.indX());
                this.cycles += 6;
                break;
            }
            case 17: {
                this.ora(this.indY(dummy.ONCARRY));
                this.cycles += 5 + this.pb;
                break;
            }
            case 170: {
                this.X = this.A;
                this.cycles += 2;
                this.setflags(this.A);
                break;
            }
            case 138: {
                this.A = this.X;
                this.cycles += 2;
                this.setflags(this.A);
                break;
            }
            case 202: {
                --this.X;
                this.X &= 0xFF;
                this.setflags(this.X);
                this.cycles += 2;
                break;
            }
            case 232: {
                ++this.X;
                this.X &= 0xFF;
                this.setflags(this.X);
                this.cycles += 2;
                break;
            }
            case 168: {
                this.Y = this.A;
                this.cycles += 2;
                this.setflags(this.A);
                break;
            }
            case 152: {
                this.A = this.Y;
                this.cycles += 2;
                this.setflags(this.A);
                break;
            }
            case 136: {
                --this.Y;
                this.Y &= 0xFF;
                this.setflags(this.Y);
                this.cycles += 2;
                break;
            }
            case 200: {
                ++this.Y;
                this.Y &= 0xFF;
                this.setflags(this.Y);
                this.cycles += 2;
                break;
            }
            case 35: {
                this.rla(this.indX());
                this.cycles += 8;
                break;
            }
            case 51: {
                this.rla(this.indY(dummy.ALWAYS));
                this.cycles += 8;
                break;
            }
            case 39: {
                this.rla(this.zpg());
                this.cycles += 5;
                break;
            }
            case 55: {
                this.rla(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 59: {
                this.rla(this.abs(this.Y, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 47: {
                this.rla(this.abs());
                this.cycles += 6;
                break;
            }
            case 63: {
                this.rla(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 42: {
                this.rolA();
                this.cycles += 2;
                break;
            }
            case 38: {
                this.rol(this.zpg());
                this.cycles += 5;
                break;
            }
            case 54: {
                this.rol(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 46: {
                this.rol(this.abs());
                this.cycles += 6;
                break;
            }
            case 62: {
                this.rol(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 106: {
                this.rorA();
                this.cycles += 2;
                break;
            }
            case 102: {
                this.ror(this.zpg());
                this.cycles += 5;
                break;
            }
            case 118: {
                this.ror(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 110: {
                this.ror(this.abs());
                this.cycles += 6;
                break;
            }
            case 126: {
                this.ror(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 99: {
                this.rra(this.indX());
                this.cycles += 8;
                break;
            }
            case 115: {
                this.rra(this.indY(dummy.ALWAYS));
                this.cycles += 8;
                break;
            }
            case 103: {
                this.rra(this.zpg());
                this.cycles += 5;
                break;
            }
            case 119: {
                this.rra(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 123: {
                this.rra(this.abs(this.Y, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 111: {
                this.rra(this.abs());
                this.cycles += 6;
                break;
            }
            case 127: {
                this.rra(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 64: {
                this.rti();
                this.cycles += 6;
                break;
            }
            case 96: {
                this.rts();
                this.cycles += 6;
                break;
            }
            case 131: {
                this.sax(this.indX());
                this.cycles += 6;
                break;
            }
            case 135: {
                this.sax(this.zpg());
                this.cycles += 3;
                break;
            }
            case 151: {
                this.sax(this.zpg(this.Y));
                this.cycles += 4;
                break;
            }
            case 143: {
                this.sax(this.abs());
                this.cycles += 4;
                break;
            }
            case 225: {
                this.sbc(this.indX());
                this.cycles += 6;
                break;
            }
            case 241: {
                this.sbc(this.indY(dummy.ONCARRY));
                this.cycles += 5 + this.pb;
                break;
            }
            case 229: {
                this.sbc(this.zpg());
                this.cycles += 3;
                break;
            }
            case 245: {
                this.sbc(this.zpg(this.X));
                this.cycles += 4;
                break;
            }
            case 233: {
                this.sbc(this.imm());
                this.cycles += 2;
                break;
            }
            case 249: {
                this.sbc(this.abs(this.Y, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 235: {
                this.sbc(this.imm());
                this.cycles += 2;
                break;
            }
            case 237: {
                this.sbc(this.abs());
                this.cycles += 4;
                break;
            }
            case 253: {
                this.sbc(this.abs(this.X, dummy.ONCARRY));
                this.cycles += 4 + this.pb;
                break;
            }
            case 158: {
                this.shx(this.abs(this.Y, dummy.ALWAYS));
                this.cycles += 5;
                break;
            }
            case 156: {
                this.shy(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 5;
                break;
            }
            case 3: {
                this.slo(this.indX());
                this.cycles += 8;
                break;
            }
            case 7: {
                this.slo(this.zpg());
                this.cycles += 5;
                break;
            }
            case 15: {
                this.slo(this.abs());
                this.cycles += 6;
                break;
            }
            case 19: {
                this.slo(this.indY(dummy.ALWAYS));
                this.cycles += 8;
                break;
            }
            case 23: {
                this.slo(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 27: {
                this.slo(this.abs(this.Y, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 31: {
                this.slo(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 67: {
                this.sre(this.indX());
                this.cycles += 8;
                break;
            }
            case 83: {
                this.sre(this.indY(dummy.ALWAYS));
                this.cycles += 8;
                break;
            }
            case 71: {
                this.sre(this.zpg());
                this.cycles += 5;
                break;
            }
            case 87: {
                this.sre(this.zpg(this.X));
                this.cycles += 6;
                break;
            }
            case 91: {
                this.sre(this.abs(this.Y, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 79: {
                this.sre(this.abs());
                this.cycles += 6;
                break;
            }
            case 95: {
                this.sre(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 7;
                break;
            }
            case 133: {
                this.sta(this.zpg());
                this.cycles += 3;
                break;
            }
            case 149: {
                this.sta(this.zpg(this.X));
                this.cycles += 4;
                break;
            }
            case 141: {
                this.sta(this.abs());
                this.cycles += 4;
                break;
            }
            case 157: {
                this.sta(this.abs(this.X, dummy.ALWAYS));
                this.cycles += 5;
                break;
            }
            case 153: {
                this.sta(this.abs(this.Y, dummy.ALWAYS));
                this.cycles += 5;
                break;
            }
            case 129: {
                this.sta(this.indX());
                this.cycles += 6;
                break;
            }
            case 145: {
                this.sta(this.indY(dummy.ALWAYS));
                this.cycles += 6;
                break;
            }
            case 154: {
                this.S = this.X;
                this.cycles += 2;
                break;
            }
            case 186: {
                this.X = this.S;
                this.cycles += 2;
                this.setflags(this.X);
                break;
            }
            case 72: {
                this.ram.read(this.PC + 1);
                this.push(this.A);
                this.cycles += 3;
                break;
            }
            case 104: {
                this.ram.read(this.PC + 1);
                this.A = this.pop();
                this.setflags(this.A);
                this.cycles += 4;
                break;
            }
            case 8: {
                this.ram.read(this.PC + 1);
                this.push(this.flagstobyte() | 0x10);
                this.cycles += 3;
                break;
            }
            case 40: {
                this.delayInterrupt();
                this.ram.read(this.PC + 1);
                this.bytetoflags(this.pop());
                this.cycles += 4;
                break;
            }
            case 134: {
                this.stx(this.zpg());
                this.cycles += 3;
                break;
            }
            case 150: {
                this.stx(this.zpg(this.Y));
                this.cycles += 4;
                break;
            }
            case 142: {
                this.stx(this.abs());
                this.cycles += 4;
                break;
            }
            case 132: {
                this.sty(this.zpg());
                this.cycles += 3;
                break;
            }
            case 148: {
                this.sty(this.zpg(this.X));
                this.cycles += 4;
                break;
            }
            case 140: {
                this.sty(this.abs());
                this.cycles += 4;
                break;
            }
            case 155: {
                this.tas(this.abs(this.Y, dummy.ALWAYS));
                this.cycles += 5;
                break;
            }
            case 139: {
                this.xaa(this.imm());
                this.cycles += 2;
                break;
            }
            default: {
                this.cycles += 2;
                System.err.println("Illegal opcode:" + utils.hex(n3) + " @ " + utils.hex(this.PC - 1));
            }
        }
        this.pb = 0;
        this.PC &= 0xFFFF;
    }

    private void delayInterrupt() {
        this.interruptDelay = true;
        this.previntflag = this.interruptsDisabled;
    }

    private void rol(int n) {
        int n2 = this.ram.read(n);
        this.ram.write(n, n2);
        n2 = n2 << 1 | (this.carryFlag ? 1 : 0);
        this.carryFlag = (n2 & 0x100) != 0;
        this.setflags(n2 &= 0xFF);
        this.ram.write(n, n2);
    }

    private void rolA() {
        this.A = this.A << 1 | (this.carryFlag ? 1 : 0);
        this.carryFlag = (this.A & 0x100) != 0;
        this.A &= 0xFF;
        this.setflags(this.A);
    }

    private void ror(int n) {
        int n2 = this.ram.read(n);
        this.ram.write(n, n2);
        boolean bl = this.carryFlag;
        this.carryFlag = (n2 & 1) != 0;
        n2 >>= 1;
        n2 &= 0x7F;
        this.setflags(n2 |= bl ? 128 : 0);
        this.ram.write(n, n2);
    }

    private void rorA() {
        boolean bl = this.carryFlag;
        this.carryFlag = (this.A & 1) != 0;
        this.A >>= 1;
        this.A &= 0x7F;
        this.A |= bl ? 128 : 0;
        this.setflags(this.A);
    }

    public void setNMI(boolean bl) {
        this.nmi = bl;
    }

    private void nmi() {
        this.idle = false;
        this.log("**NMI**");
        this.push(this.PC >> 8);
        this.push(this.PC & 0xFF);
        this.push(this.flagstobyte() & 0xFFFFFFEF);
        this.PC = this.ram.read(65530) + (this.ram.read(65531) << 8);
        this.cycles += 7;
        this.interruptsDisabled = true;
    }

    private void interrupt() {
        this.idle = false;
        this.log("**INTERRUPT**");
        this.push(this.PC >> 8);
        this.push(this.PC & 0xFF);
        this.push(this.flagstobyte() & 0xFFFFFFEF);
        this.PC = this.ram.read(65534) + (this.ram.read(65535) << 8);
        this.interruptsDisabled = true;
    }

    private void breakinterrupt() {
        this.log("**BREAK**");
        this.ram.read(this.PC++);
        this.push(this.PC >> 8);
        this.push(this.PC & 0xFF);
        this.push(this.flagstobyte() | 0x10 | 0x20);
        this.PC = this.ram.read(65534) + (this.ram.read(65535) << 8);
        this.interruptsDisabled = true;
    }

    private void lsr(int n) {
        int n2 = this.ram.read(n);
        this.ram.write(n, n2);
        this.carryFlag = (n2 & 1) != 0;
        n2 >>= 1;
        this.ram.write(n, n2 &= 0x7F);
        this.setflags(n2);
    }

    private void lsrA() {
        this.carryFlag = (this.A & 1) != 0;
        this.A >>= 1;
        this.A &= 0x7F;
        this.setflags(this.A);
    }

    private void eor(int n) {
        this.A ^= this.ram.read(n);
        this.A &= 0xFF;
        this.setflags(this.A);
    }

    private void ora(int n) {
        this.A |= this.ram.read(n);
        this.A &= 0xFF;
        this.setflags(this.A);
    }

    private void bit(int n) {
        int n2 = this.ram.read(n);
        this.zeroFlag = (n2 & this.A) == 0;
        this.negativeFlag = (n2 & 0x80) != 0;
        this.overflowFlag = (n2 & 0x40) != 0;
    }

    private void jsr(int n) {
        --this.PC;
        this.ram.read(this.PC);
        this.push(this.PC >> 8);
        this.push(this.PC & 0xFF);
        this.PC = n;
    }

    private void rts() {
        this.ram.read(this.PC++);
        this.PC = this.pop() & 0xFF | this.pop() << 8;
        ++this.PC;
    }

    private void rti() {
        this.ram.read(this.PC++);
        this.bytetoflags(this.pop());
        this.PC = this.pop() & 0xFF | this.pop() << 8;
    }

    private int pop() {
        ++this.S;
        this.S &= 0xFF;
        return this.ram.read(256 + this.S);
    }

    public void push(int n) {
        this.ram.write(256 + (this.S & 0xFF), n);
        --this.S;
        this.S &= 0xFF;
    }

    private void branch(boolean bl) {
        if (bl) {
            int n = this.PC + 1;
            this.PC = this.rel();
            if ((n & 0xFF00) != (this.PC & 0xFF00)) {
                this.pb = 2;
            } else {
                ++this.cycles;
            }
            if (n - 2 == this.PC) {
                this.idle = true;
            }
        } else {
            this.rel();
        }
    }

    private void inc(int n) {
        int n2 = this.ram.read(n);
        this.ram.write(n, n2);
        ++n2;
        this.ram.write(n, n2 &= 0xFF);
        this.setflags(n2);
    }

    private void dec(int n) {
        int n2 = this.ram.read(n);
        this.ram.write(n, n2);
        --n2;
        this.ram.write(n, n2 &= 0xFF);
        this.setflags(n2);
    }

    private void adc(int n) {
        int n2 = this.ram.read(n);
        if (this.decimalModeFlag) {
            // empty if block
        }
        int n3 = n2 + this.A + (this.carryFlag ? 1 : 0);
        this.carryFlag = n3 >> 8 != 0;
        this.overflowFlag = ((this.A ^ n2) & 0x80) == 0 && ((this.A ^ n3) & 0x80) != 0;
        this.A = n3 & 0xFF;
        this.setflags(this.A);
    }

    private void sbc(int n) {
        int n2 = this.ram.read(n);
        if (this.decimalModeFlag) {
            // empty if block
        }
        int n3 = this.A - n2 - (this.carryFlag ? 0 : 1);
        this.carryFlag = n3 >> 8 == 0;
        this.overflowFlag = ((this.A ^ n2) & 0x80) != 0 && ((this.A ^ n3) & 0x80) != 0;
        this.A = n3 & 0xFF;
        this.setflags(this.A);
    }

    private void and(int n) {
        this.A &= this.ram.read(n);
        this.setflags(this.A);
    }

    private void asl(int n) {
        int n2 = this.ram.read(n);
        this.ram.write(n, n2);
        this.carryFlag = (n2 & 0x80) != 0;
        n2 <<= 1;
        this.setflags(n2 &= 0xFF);
        this.ram.write(n, n2);
    }

    private void aslA() {
        this.carryFlag = (this.A & 0x80) != 0;
        this.A <<= 1;
        this.A &= 0xFF;
        this.setflags(this.A);
    }

    private void cmp(int n, int n2) {
        int n3 = n - this.ram.read(n2);
        if (n3 < 0) {
            this.negativeFlag = (n3 & 0x80) != 0;
            this.carryFlag = false;
            this.zeroFlag = false;
        } else if (n3 == 0) {
            this.negativeFlag = false;
            this.carryFlag = true;
            this.zeroFlag = true;
        } else {
            this.negativeFlag = (n3 & 0x80) != 0;
            this.carryFlag = true;
            this.zeroFlag = false;
        }
    }

    private void lda(int n) {
        this.A = this.ram.read(n);
        this.setflags(this.A);
    }

    private void ldx(int n) {
        this.X = this.ram.read(n);
        this.setflags(this.X);
    }

    private void ldy(int n) {
        this.Y = this.ram.read(n);
        this.setflags(this.Y);
    }

    private void setflags(int n) {
        this.zeroFlag = n == 0;
        this.negativeFlag = (n & 0x80) != 0;
    }

    private void sta(int n) {
        this.hackAddr = n;
        this.hackData = this.A;
        this.dirtyBattletoadsHack = true;
    }

    private void stx(int n) {
        this.hackAddr = n;
        this.hackData = this.X;
        this.dirtyBattletoadsHack = true;
    }

    private void sty(int n) {
        this.hackAddr = n;
        this.hackData = this.Y;
        this.dirtyBattletoadsHack = true;
    }

    private void ahx(int n) {
        int n2 = this.A & this.X & (n >> 8) + 1 & 0xFF;
        int n3 = n - this.Y & 0xFF;
        if (this.Y + n3 <= 255) {
            this.ram.write(n, n2);
        } else {
            this.ram.write(n, this.ram.read(n));
        }
    }

    private void alr(int n) {
        this.and(n);
        this.lsrA();
    }

    private void anc(int n) {
        this.and(n);
        this.carryFlag = this.negativeFlag;
    }

    private void arr(int n) {
        this.A = (this.ram.read(n) & this.A) >> 1 | (this.carryFlag ? 128 : 0);
        this.setflags(this.A);
        this.carryFlag = (this.A & 0x40) != 0;
        this.overflowFlag = this.carryFlag ^ (this.A & 0x20) != 0;
    }

    private void axs(int n) {
        this.X = (this.A & this.X) - this.ram.read(n) & 0xFF;
        this.setflags(this.X);
        this.carryFlag = this.X >= 0;
    }

    private void dcp(int n, int n2) {
        this.dec(n2);
        this.cmp(n, n2);
    }

    private void las(int n) {
        this.S &= this.ram.read(n);
        this.A = this.X = this.S;
        this.setflags(this.S);
    }

    private void lax(int n) {
        this.A = this.X = this.ram.read(n);
        this.setflags(this.A);
    }

    private void isc(int n) {
        this.inc(n);
        this.sbc(n);
    }

    private void rla(int n) {
        this.rol(n);
        this.and(n);
    }

    private void rra(int n) {
        this.ror(n);
        this.adc(n);
    }

    private void sax(int n) {
        this.ram.write(n, this.A & this.X & 0xFF);
    }

    private void shx(int n) {
        int n2 = this.X & (n >> 8) + 1 & 0xFF;
        int n3 = n - this.Y & 0xFF;
        if (this.Y + n3 <= 255) {
            this.ram.write(n, n2);
        } else {
            this.ram.write(n, this.ram.read(n));
        }
    }

    private void shy(int n) {
        int n2 = this.Y & (n >> 8) + 1 & 0xFF;
        int n3 = n - this.X & 0xFF;
        if (this.X + n3 <= 255) {
            this.ram.write(n, n2);
        } else {
            this.ram.write(n, this.ram.read(n));
        }
    }

    private void slo(int n) {
        this.asl(n);
        this.ora(n);
    }

    private void sre(int n) {
        this.lsr(n);
        this.eor(n);
    }

    private void tas(int n) {
        this.S = this.A & this.X;
        int n2 = this.S & (n >> 8) + 1 & 0xFF;
        int n3 = n - this.Y & 0xFF;
        if (this.Y + n3 <= 255) {
            this.ram.write(n, n2);
        } else {
            this.ram.write(n, this.ram.read(n));
        }
    }

    private void xaa(int n) {
        this.A = this.X & this.ram.read(n);
        this.setflags(this.A);
    }

    private int imm() {
        return this.PC++;
    }

    private int zpg() {
        return this.ram.read(this.PC++);
    }

    private int zpg(int n) {
        return this.ram.read(this.PC++) + n & 0xFF;
    }

    private int rel() {
        return (byte)this.ram.read(this.PC++) + this.PC;
    }

    private int abs() {
        return this.ram.read(this.PC++) + (this.ram.read(this.PC++) << 8);
    }

    private int abs(int n, dummy dummy2) {
        int n2;
        if ((n2 = this.ram.read(this.PC++) | this.ram.read(this.PC++) << 8) >> 8 != n2 + n >> 8) {
            this.pb = 1;
        }
        if ((n2 & 0xFF00) != (n2 + n & 0xFF00)) {
            if (dummy2 == dummy.ONCARRY) {
                this.ram.read(n2 & 0xFF00 | n2 + n & 0xFF);
            }
        }
        if (dummy2 == dummy.ALWAYS) {
            this.ram.read(n2 & 0xFF00 | n2 + n & 0xFF);
        }
        return n2 + n & 0xFFFF;
    }

    private int ind() {
        int n = this.abs();
        return this.ram.read(n) + (this.ram.read((n & 0xFF) == 255 ? n - 255 : n + 1) << 8);
    }

    private int indX() {
        int n = this.ram.read(this.PC++);
        return this.ram.read(n + this.X & 0xFF) + (this.ram.read(n + 1 + this.X & 0xFF) << 8);
    }

    private int indY(dummy dummy2) {
        int n;
        int n2;
        if ((n2 = this.ram.read((n = this.ram.read(this.PC++)) & 0xFF) | this.ram.read(n + 1 & 0xFF) << 8) >> 8 != n2 + this.Y >> 8) {
            this.pb = 1;
        }
        if ((n2 & 0xFF00) != (n2 + this.Y & 0xFF00)) {
            if (dummy2 == dummy.ONCARRY) {
                this.ram.read(n2 & 0xFF00 | n2 + this.Y & 0xFF);
            }
        }
        if (dummy2 == dummy.ALWAYS) {
            this.ram.read(n2 & 0xFF00 | n2 + this.Y & 0xFF);
        }
        return n2 + this.Y & 0xFFFF;
    }

    public final int flagstobyte() {
        return (this.negativeFlag ? 128 : 0) | (this.overflowFlag ? 64 : 0) | 0x20 | (this.decimalModeFlag ? 8 : 0) | (this.interruptsDisabled ? 4 : 0) | (this.zeroFlag ? 2 : 0) | (this.carryFlag ? 1 : 0);
    }

    private void bytetoflags(int n) {
        this.negativeFlag = (n & 0x80) != 0;
        this.overflowFlag = (n & 0x40) != 0;
        this.decimalModeFlag = (n & 8) != 0;
        this.interruptsDisabled = (n & 4) != 0;
        this.zeroFlag = (n & 2) != 0;
        this.carryFlag = (n & 1) != 0;
    }

    public String status() {
        return " PC:" + utils.hex(this.PC) + " A:" + utils.hex(this.A) + " X:" + utils.hex(this.X) + " Y:" + utils.hex(this.Y) + " P:" + utils.hex(this.flagstobyte()) + " SP:" + utils.hex(this.S);
    }

    public static String[] opcodes() {
        String[] stringArray = new String[]{"BRK", "ORA $(%2$02X%1$02X,x)", "KIL", "SLO $(%2$02X%1$02X,x)", "NOP $%1$02X", "ORA $%1$02X", "ASL $%1$02X", "SLO $%1$02X", "PHP", "ORA #$%1$02X", "ASL A", "ANC #$%1$02X", "NOP $%2$02X%1$02X", "ORA $%2$02X%1$02X", "ASL $%2$02X%1$02X", "SLO $%2$02X%1$02X", "BPL $%3$02X", "ORA ($%1$02X), y", "KIL", "SLO ($%1$02X), y", "NOP $%1$02X,x", "ORA $%1$02X,x", "ASL $%1$02X,x", "SLO $%1$02X,x", "CLC", "ORA $%2$02X%1$02X,y", "NOP", "SLO $%2$02X%1$02X,y", "NOP $%2$02X%1$02X,x", "ORA $%2$02X%1$02X,x", "ASL $%2$02X%1$02X,x", "SLO $%2$02X%1$02X,x", "JSR $%2$02X%1$02X", "AND $(%2$02X%1$02X,x)", "KIL", "RLA $(%2$02X%1$02X,x)", "BIT $%1$02X", "AND $%1$02X", "ROL $%1$02X", "RLA $%1$02X", "PLP", "AND #$%1$02X", "ROL", "ANC #$%1$02X", "BIT $%2$02X%1$02X", "AND $%2$02X%1$02X", "ROL $%2$02X%1$02X", "RLA $%2$02X%1$02X", "BMI $%3$02X", "AND ($%1$02X), y", "KIL", "RLA ($%1$02X), y", "NOP $%1$02X,x", "AND $%1$02X,x", "ROL $%1$02X,x", "RLA $%1$02X,x", "SEC", "AND $%2$02X%1$02X,y", "NOP", "RLA $%2$02X%1$02X,y", "NOP $%2$02X%1$02X,x", "AND $%2$02X%1$02X,x", "ROL $%2$02X%1$02X,x", "RLA $%2$02X%1$02X,x", "RTI", "EOR $(%2$02X%1$02X,x)", "KIL", "SRE $(%2$02X%1$02X,x)", "NOP $%1$02X", "EOR $%1$02X", "LSR $%1$02X", "SRE $%1$02X", "PHA", "EOR #$%1$02X", "LSR", "ALR #$%1$02X", "JMP $%2$02X%1$02X", "EOR $%2$02X%1$02X", "LSR $%2$02X%1$02X", "SRE $%2$02X%1$02X", "BVC $%3$02X", "EOR ($%1$02X), y", "KIL", "SRE ($%1$02X), y", "NOP $%1$02X,x", "EOR $%1$02X,x", "LSR $%1$02X,x", "SRE $%1$02X,x", "CLI", "EOR $%2$02X%1$02X,y", "NOP", "SRE $%2$02X%1$02X,y", "NOP $%2$02X%1$02X,x", "EOR $%2$02X%1$02X,x", "LSR $%2$02X%1$02X,x", "SRE $%2$02X%1$02X,x", "RTS", "ADC $(%2$02X%1$02X,x)", "KIL", "RRA $(%2$02X%1$02X,x)", "NOP $%1$02X", "ADC $%1$02X", "ROR $%1$02X", "RRA $%1$02X", "PLA", "ADC #$%1$02X", "ROR", "ARR #$%1$02X", "JMP ($%2$02X%1$02X)", "ADC $%2$02X%1$02X", "ROR $%2$02X%1$02X", "RRA $%2$02X%1$02X", "BVS $%3$02X", "ADC ($%1$02X), y", "KIL", "RRA ($%1$02X), y", "NOP $%1$02X,x", "ADC $%1$02X,x", "ROR $%1$02X,x", "RRA $%1$02X,x", "SEI", "ADC $%2$02X%1$02X,y", "NOP", "RRA $%2$02X%1$02X,y", "NOP $%2$02X%1$02X,x", "ADC $%2$02X%1$02X,x", "ROR $%2$02X%1$02X,x", "RRA $%2$02X%1$02X,x", "NOP #$%1$02X", "STA $(%2$02X%1$02X,x)", "NOP #$%1$02X", "SAX $(%2$02X%1$02X,x)", "STY $%1$02X", "STA $%1$02X", "STX $%1$02X", "SAX $%1$02X", "DEY", "NOP #$%1$02X", "TXA", "XAA #$%1$02X", "STY $%2$02X%1$02X", "STA $%2$02X%1$02X", "STX $%2$02X%1$02X", "SAX $%2$02X%1$02X", "BCC $%3$02X", "STA ($%1$02X), y", "KIL", "AHX ($%1$02X), y", "STY $%1$02X,x", "STA $%1$02X,x", "STX $%1$02X,y", "SAX $%1$02X,y", "TYA", "STA $%2$02X%1$02X,y", "TXS", "TAS $%2$02X%1$02X,y", "SHY $%2$02X%1$02X,x", "STA $%2$02X%1$02X,x", "SHX $%2$02X%1$02X,y", "AHX $%2$02X%1$02X,y", "LDY #$%1$02X", "LDA $(%2$02X%1$02X,x)", "LDX #$%1$02X", "LAX $(%2$02X%1$02X,x)", "LDY $%1$02X", "LDA $%1$02X", "LDX $%1$02X", "LAX $%1$02X", "TAY", "LDA #$%1$02X", "TAX", "LAX #$%1$02X", "LDY $%2$02X%1$02X", "LDA $%2$02X%1$02X", "LDX $%2$02X%1$02X", "LAX $%2$02X%1$02X", "BCS $%3$02X", "LDA ($%1$02X), y", "KIL", "LAX ($%1$02X), y", "LDY $%1$02X,x", "LDA $%1$02X,x", "LDX $%1$02X,y", "LAX $%1$02X,y", "CLV", "LDA $%2$02X%1$02X,y", "TSX", "LAS $%2$02X%1$02X,y", "LDY $%2$02X%1$02X,x", "LDA $%2$02X%1$02X,x", "LDX $%2$02X%1$02X,y", "LAX $%2$02X%1$02X,y", "CPY #$%1$02X", "CMP $(%2$02X%1$02X,x)", "NOP #$%1$02X", "DCP $(%2$02X%1$02X,x)", "CPY $%1$02X", "CMP $%1$02X", "DEC $%1$02X", "DCP $%1$02X", "INY", "CMP #$%1$02X", "DEX", "AXS #$%1$02X", "CPY $%2$02X%1$02X", "CMP $%2$02X%1$02X", "DEC $%2$02X%1$02X", "DCP $%2$02X%1$02X", "BNE $%3$02X", "CMP ($%1$02X), y", "KIL", "DCP ($%1$02X), y", "NOP $%1$02X,x", "CMP $%1$02X,x", "DEC $%1$02X,x", "DCP $%1$02X,x", "CLD", "CMP $%2$02X%1$02X,y", "NOP", "DCP $%2$02X%1$02X,y", "NOP $%2$02X%1$02X,x", "CMP $%2$02X%1$02X,x", "DEC $%2$02X%1$02X,x", "DCP $%2$02X%1$02X,x", "CPX #$%1$02X", "SBC $(%2$02X%1$02X,x)", "NOP #$%1$02X", "ISC $(%2$02X%1$02X,x)", "CPX $%1$02X", "SBC $%1$02X", "INC $%1$02X", "ISC $%1$02X", "INX", "SBC #$%1$02X", "NOP", "SBC #$%1$02X", "CPX $%2$02X%1$02X", "SBC $%2$02X%1$02X", "INC $%2$02X%1$02X", "ISC $%2$02X%1$02X", "BEQ $%3$02X", "SBC ($%1$02X), y", "KIL", "ISC ($%1$02X), y", "NOP $%1$02X,x", "SBC $%1$02X,x", "INC $%1$02X,x", "ISC $%1$02X,x", "SED", "SBC $%2$02X%1$02X,y", "NOP", "ISC $%2$02X%1$02X,y", "NOP $%2$02X%1$02X,x", "SBC $%2$02X%1$02X,x", "INC $%2$02X%1$02X,x", "ISC $%2$02X%1$02X,x"};
        return stringArray;
    }

    public void setRegA(int n) {
        this.A = n & 0xFF;
    }

    public void setRegX(int n) {
        this.X = n & 0xFF;
    }

    public void setPC(int n) {
        this.PC = n & 0xFFFF;
        this.idle = false;
        this.log("**PC SET**");
    }

    public final void log(String string) {
        if (this.logging) {
            try {
                this.w.write(string);
            }
            catch (IOException iOException) {
                System.err.println("Cannot write to debug log" + iOException.getLocalizedMessage());
            }
        }
    }

    private void flushLog() {
        if (this.logging) {
            try {
                this.w.flush();
            }
            catch (IOException iOException) {
                System.err.println("Cannot write to debug log" + iOException.getLocalizedMessage());
            }
        }
    }

    private static enum dummy {
        ONCARRY,
        ALWAYS;

    }
}

