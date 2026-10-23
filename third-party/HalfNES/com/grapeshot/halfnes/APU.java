/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes;

import com.grapeshot.halfnes.CPU;
import com.grapeshot.halfnes.CPURAM;
import com.grapeshot.halfnes.NES;
import com.grapeshot.halfnes.PrefsSingleton;
import com.grapeshot.halfnes.audio.AudioOutInterface;
import com.grapeshot.halfnes.audio.ExpansionSoundChip;
import com.grapeshot.halfnes.audio.NoiseTimer;
import com.grapeshot.halfnes.audio.SquareTimer;
import com.grapeshot.halfnes.audio.SwingAudioImpl;
import com.grapeshot.halfnes.audio.Timer;
import com.grapeshot.halfnes.audio.TriangleTimer;
import com.grapeshot.halfnes.mappers.Mapper;
import com.grapeshot.halfnes.ui.Oscilloscope;
import java.util.ArrayList;

public class APU {
    public int samplerate = 1;
    private final Timer[] timers = new Timer[]{new SquareTimer(8, 2), new SquareTimer(8, 2), new TriangleTimer(), new NoiseTimer()};
    private double cyclespersample;
    public final NES nes;
    CPU cpu;
    CPURAM cpuram;
    public int sprdma_count;
    private int apucycle = 0;
    private int remainder = 0;
    private int[] noiseperiod;
    private long accum = 0L;
    private final ArrayList<ExpansionSoundChip> expnSound = new ArrayList();
    private boolean soundFiltering;
    private static final int[] TNDLOOKUP = APU.initTndLookup();
    private static final int[] SQUARELOOKUP = APU.initSquareLookup();
    private int framectrreload;
    private int framectrdiv = 7456;
    private int dckiller = -6392;
    private int lpaccum = 0;
    private boolean apuintflag = true;
    private boolean statusdmcint = false;
    private boolean statusframeint = false;
    private int framectr = 0;
    private int ctrmode = 4;
    private final boolean[] lenCtrEnable = new boolean[]{true, true, true, true};
    private final int[] volume = new int[4];
    private int[] dmcperiods;
    private int dmcrate = 54;
    private int dmcpos = 0;
    private int dmcshiftregister = 0;
    private int dmcbuffer = 0;
    private int dmcvalue = 0;
    private int dmcsamplelength = 1;
    private int dmcsamplesleft = 0;
    private int dmcstartaddr = 49152;
    private int dmcaddr = 49152;
    private int dmcbitsleft = 8;
    private boolean dmcsilence = true;
    private boolean dmcirq = false;
    private boolean dmcloop = false;
    private boolean dmcBufferEmpty = true;
    private final int[] lengthctr = new int[]{0, 0, 0, 0};
    private static final int[] lenctrload = new int[]{10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14, 12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30};
    private final boolean[] lenctrHalt = new boolean[]{true, true, true, true};
    private int linearctr = 0;
    private int linctrreload = 0;
    private boolean linctrflag = false;
    private final int[] envelopeValue = new int[]{15, 15, 15, 15};
    private final int[] envelopeCounter = new int[]{0, 0, 0, 0};
    private final int[] envelopePos = new int[]{0, 0, 0, 0};
    private final boolean[] envConstVolume = new boolean[]{true, true, true, true};
    private final boolean[] envelopeStartFlag = new boolean[]{false, false, false, false};
    private final boolean[] sweepenable = new boolean[]{false, false};
    private final boolean[] sweepnegate = new boolean[]{false, false};
    private final boolean[] sweepsilence = new boolean[]{false, false};
    private final boolean[] sweepreload = new boolean[]{false, false};
    private final int[] sweepperiod = new int[]{15, 15};
    private final int[] sweepshift = new int[]{0, 0};
    private final int[] sweeppos = new int[]{0, 0};
    private int cyclesperframe;
    private AudioOutInterface ai;
    private static final int[][] DUTYLOOKUP = new int[][]{{0, 1, 0, 0, 0, 0, 0, 0}, {0, 1, 1, 0, 0, 0, 0, 0}, {0, 1, 1, 1, 1, 0, 0, 0}, {1, 0, 0, 1, 1, 1, 1, 1}};

    public APU(NES nES, CPU cPU, CPURAM cPURAM) {
        this.nes = nES;
        this.cpu = cPU;
        this.cpuram = cPURAM;
        this.setParameters();
    }

    private static int[] initTndLookup() {
        int[] nArray = new int[203];
        for (int i = 0; i < nArray.length; ++i) {
            nArray[i] = (int)(163.67 / (24329.0 / (double)i + 100.0) * 49151.0);
        }
        return nArray;
    }

    private static int[] initSquareLookup() {
        int[] nArray = new int[31];
        for (int i = 0; i < nArray.length; ++i) {
            nArray[i] = (int)(95.52 / (8128.0 / (double)i + 100.0) * 49151.0);
        }
        return nArray;
    }

    public final synchronized void setParameters() {
        Mapper.TVType tVType = this.cpuram.mapper.getTVType();
        this.soundFiltering = PrefsSingleton.get().getBoolean("soundFiltering", true);
        this.samplerate = PrefsSingleton.get().getInt("sampleRate", 44100);
        if (this.ai != null) {
            this.ai.destroy();
        }
        this.ai = new SwingAudioImpl(this.nes, this.samplerate, tVType);
        if (PrefsSingleton.get().getBoolean("showScope", false)) {
            this.ai = new Oscilloscope(this.ai);
        }
        switch (tVType) {
            default: {
                this.dmcperiods = new int[]{428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54};
                this.noiseperiod = new int[]{4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068};
                this.framectrreload = 7456;
                this.cyclespersample = 1789773.0 / (double)this.samplerate;
                this.cyclesperframe = 29781;
                break;
            }
            case DENDY: {
                this.dmcperiods = new int[]{428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54};
                this.noiseperiod = new int[]{4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068};
                this.framectrreload = 7456;
                this.cyclespersample = 1773448.0 / (double)this.samplerate;
                this.cyclesperframe = 35469;
                break;
            }
            case PAL: {
                this.cyclespersample = 1662607.0 / (double)this.samplerate;
                this.dmcperiods = new int[]{398, 354, 316, 298, 276, 236, 210, 198, 176, 148, 132, 118, 98, 78, 66, 50};
                this.noiseperiod = new int[]{4, 8, 14, 30, 60, 88, 118, 148, 188, 236, 354, 472, 708, 944, 1890, 3778};
                this.framectrreload = 8312;
                this.cyclesperframe = 33252;
            }
        }
    }

    public boolean bufferHasLessThan(int n) {
        return this.ai.bufferHasLessThan(n);
    }

    public final int read(int n) {
        this.updateto(this.cpu.clocks);
        switch (n) {
            case 21: {
                int n2 = (this.lengthctr[0] > 0 ? 1 : 0) | (this.lengthctr[1] > 0 ? 2 : 0) | (this.lengthctr[2] > 0 ? 4 : 0) | (this.lengthctr[3] > 0 ? 8 : 0) | (this.dmcsamplesleft > 0 ? 16 : 0) | (this.statusframeint ? 64 : 0) | (this.statusdmcint ? 128 : 0);
                if (this.statusframeint) {
                    --this.cpu.interrupt;
                    this.statusframeint = false;
                }
                return n2;
            }
            case 22: {
                this.nes.getcontroller1().strobe();
                return this.nes.getcontroller1().getbyte() | 0x40;
            }
            case 23: {
                this.nes.getcontroller2().strobe();
                return this.nes.getcontroller2().getbyte() | 0x40;
            }
        }
        return 64;
    }

    public void addExpnSound(ExpansionSoundChip expansionSoundChip) {
        this.expnSound.add(expansionSoundChip);
    }

    public void destroy() {
        this.ai.destroy();
    }

    public void pause() {
        this.ai.pause();
    }

    public void resume() {
        this.ai.resume();
    }

    public final void write(int n, int n2) {
        this.updateto(this.cpu.clocks - 1);
        switch (n) {
            case 0: {
                this.lenctrHalt[0] = (n2 & 0x20) != 0;
                this.timers[0].setduty(DUTYLOOKUP[n2 >> 6]);
                this.envConstVolume[0] = (n2 & 0x10) != 0;
                this.envelopeValue[0] = n2 & 0xF;
                break;
            }
            case 1: {
                this.sweepenable[0] = (n2 & 0x80) != 0;
                this.sweepperiod[0] = n2 >> 4 & 7;
                this.sweepnegate[0] = (n2 & 8) != 0;
                this.sweepshift[0] = n2 & 7;
                this.sweepreload[0] = true;
                break;
            }
            case 2: {
                this.timers[0].setperiod((this.timers[0].getperiod() & 0xFE00) + (n2 << 1));
                break;
            }
            case 3: {
                if (this.lenCtrEnable[0]) {
                    this.lengthctr[0] = lenctrload[n2 >> 3];
                }
                this.timers[0].setperiod((this.timers[0].getperiod() & 0x1FF) + ((n2 & 7) << 9));
                this.timers[0].reset();
                this.envelopeStartFlag[0] = true;
                break;
            }
            case 4: {
                this.lenctrHalt[1] = (n2 & 0x20) != 0;
                this.timers[1].setduty(DUTYLOOKUP[n2 >> 6]);
                this.envConstVolume[1] = (n2 & 0x10) != 0;
                this.envelopeValue[1] = n2 & 0xF;
                break;
            }
            case 5: {
                this.sweepenable[1] = (n2 & 0x80) != 0;
                this.sweepperiod[1] = n2 >> 4 & 7;
                this.sweepnegate[1] = (n2 & 8) != 0;
                this.sweepshift[1] = n2 & 7;
                this.sweepreload[1] = true;
                break;
            }
            case 6: {
                this.timers[1].setperiod((this.timers[1].getperiod() & 0xFE00) + (n2 << 1));
                break;
            }
            case 7: {
                if (this.lenCtrEnable[1]) {
                    this.lengthctr[1] = lenctrload[n2 >> 3];
                }
                this.timers[1].setperiod((this.timers[1].getperiod() & 0x1FF) + ((n2 & 7) << 9));
                this.timers[1].reset();
                this.envelopeStartFlag[1] = true;
                break;
            }
            case 8: {
                this.linctrreload = n2 & 0x7F;
                this.lenctrHalt[2] = (n2 & 0x80) != 0;
                break;
            }
            case 9: {
                break;
            }
            case 10: {
                this.timers[2].setperiod((this.timers[2].getperiod() * 1 & 0xFF00) + n2);
                break;
            }
            case 11: {
                if (this.lenCtrEnable[2]) {
                    this.lengthctr[2] = lenctrload[n2 >> 3];
                }
                this.timers[2].setperiod((this.timers[2].getperiod() * 1 & 0xFF) + ((n2 & 7) << 8));
                this.linctrflag = true;
                break;
            }
            case 12: {
                this.lenctrHalt[3] = (n2 & 0x20) != 0;
                this.envConstVolume[3] = (n2 & 0x10) != 0;
                this.envelopeValue[3] = n2 & 0xF;
                break;
            }
            case 13: {
                break;
            }
            case 14: {
                this.timers[3].setduty((n2 & 0x80) != 0 ? 6 : 1);
                this.timers[3].setperiod(this.noiseperiod[n2 & 0xF]);
                break;
            }
            case 15: {
                if (this.lenCtrEnable[3]) {
                    this.lengthctr[3] = lenctrload[n2 >> 3];
                }
                this.envelopeStartFlag[3] = true;
                break;
            }
            case 16: {
                this.dmcirq = (n2 & 0x80) != 0;
                this.dmcloop = (n2 & 0x40) != 0;
                this.dmcrate = this.dmcperiods[n2 & 0xF];
                if (this.dmcirq || !this.statusdmcint) break;
                --this.cpu.interrupt;
                this.statusdmcint = false;
                break;
            }
            case 17: {
                this.dmcvalue = n2 & 0x7F;
                break;
            }
            case 18: {
                this.dmcstartaddr = (n2 << 6) + 49152;
                break;
            }
            case 19: {
                this.dmcsamplelength = (n2 << 4) + 1;
                break;
            }
            case 20: {
                for (int i = 0; i < 256; ++i) {
                    this.cpuram.write(8196, this.cpuram.read((n2 << 8) + i));
                }
                this.sprdma_count = 2;
                break;
            }
            case 21: {
                for (int i = 0; i < 4; ++i) {
                    boolean bl = this.lenCtrEnable[i] = (n2 & 1 << i) != 0;
                    if (this.lenCtrEnable[i]) continue;
                    this.lengthctr[i] = 0;
                }
                if ((n2 & 0x10) != 0) {
                    if (this.dmcsamplesleft == 0) {
                        this.restartdmc();
                    }
                } else {
                    this.dmcsamplesleft = 0;
                    this.dmcsilence = true;
                }
                if (!this.statusdmcint) break;
                --this.cpu.interrupt;
                this.statusdmcint = false;
                break;
            }
            case 22: {
                this.nes.getcontroller1().output((n2 & 1) != 0);
                this.nes.getcontroller2().output((n2 & 1) != 0);
                break;
            }
            case 23: {
                this.ctrmode = (n2 & 0x80) != 0 ? 5 : 4;
                this.apuintflag = (n2 & 0x40) != 0;
                this.framectr = 0;
                this.framectrdiv = this.framectrreload + 8;
                if (this.apuintflag && this.statusframeint) {
                    this.statusframeint = false;
                    --this.cpu.interrupt;
                }
                if (this.ctrmode != 5) break;
                this.setenvelope();
                this.setlinctr();
                this.setlength();
                this.setsweep();
                break;
            }
        }
    }

    public final void updateto(int n) {
        if (this.soundFiltering) {
            while (this.apucycle < n) {
                ++this.remainder;
                this.clockdmc();
                if (--this.framectrdiv <= 0) {
                    this.framectrdiv = this.framectrreload;
                    this.clockframecounter();
                }
                this.timers[0].clock();
                this.timers[1].clock();
                if (this.lengthctr[2] > 0 && this.linearctr > 0) {
                    this.timers[2].clock();
                }
                this.timers[3].clock();
                if (!this.expnSound.isEmpty()) {
                    for (ExpansionSoundChip expansionSoundChip : this.expnSound) {
                        expansionSoundChip.clock(1);
                    }
                }
                this.accum += (long)this.getOutputLevel();
                if ((double)this.apucycle % this.cyclespersample < 1.0) {
                    this.ai.outputSample(this.lowpass_filter(this.highpass_filter((int)(this.accum / (long)this.remainder))));
                    this.remainder = 0;
                    this.accum = 0L;
                }
                ++this.apucycle;
            }
        } else {
            while (this.apucycle < n) {
                ++this.remainder;
                this.clockdmc();
                if (--this.framectrdiv <= 0) {
                    this.framectrdiv = this.framectrreload;
                    this.clockframecounter();
                }
                if ((double)this.apucycle % this.cyclespersample < 1.0) {
                    this.timers[0].clock(this.remainder);
                    this.timers[1].clock(this.remainder);
                    if (this.lengthctr[2] > 0 && this.linearctr > 0) {
                        this.timers[2].clock(this.remainder);
                    }
                    this.timers[3].clock(this.remainder);
                    int n2 = this.getOutputLevel();
                    if (!this.expnSound.isEmpty()) {
                        for (ExpansionSoundChip expansionSoundChip : this.expnSound) {
                            expansionSoundChip.clock(this.remainder);
                        }
                    }
                    this.remainder = 0;
                    this.ai.outputSample(this.lowpass_filter(this.highpass_filter(n2)));
                }
                ++this.apucycle;
            }
        }
    }

    private int getOutputLevel() {
        int n = SQUARELOOKUP[this.volume[0] * this.timers[0].getval() + this.volume[1] * this.timers[1].getval()];
        n += TNDLOOKUP[3 * this.timers[2].getval() + 2 * this.volume[3] * this.timers[3].getval() + this.dmcvalue];
        if (!this.expnSound.isEmpty()) {
            n = (int)((double)n * 0.8);
            for (ExpansionSoundChip expansionSoundChip : this.expnSound) {
                n += expansionSoundChip.getval();
            }
        }
        return n;
    }

    private int highpass_filter(int n) {
        this.dckiller += (n -= this.dckiller) >> 8;
        this.dckiller += n > 0 ? 1 : -1;
        return n;
    }

    private int lowpass_filter(int n) {
        this.lpaccum = (int)((double)this.lpaccum + 0.5 * (double)(n - this.lpaccum));
        return this.lpaccum;
    }

    public final void finishframe() {
        this.updateto(this.cyclesperframe);
        this.apucycle = 0;
        this.ai.flushFrame(this.nes.isFrameLimiterOn());
    }

    private void clockframecounter() {
        if (this.ctrmode == 4 || this.ctrmode == 5 && this.framectr != 3) {
            this.setenvelope();
            this.setlinctr();
        }
        if (this.ctrmode == 4 && (this.framectr == 1 || this.framectr == 3) || this.ctrmode == 5 && (this.framectr == 1 || this.framectr == 4)) {
            this.setlength();
            this.setsweep();
        }
        if (!this.apuintflag && this.framectr == 3 && this.ctrmode == 4 && !this.statusframeint) {
            ++this.cpu.interrupt;
            this.statusframeint = true;
        }
        ++this.framectr;
        this.framectr %= this.ctrmode;
        this.setvolumes();
    }

    private void setvolumes() {
        int n = this.lengthctr[0] <= 0 || this.sweepsilence[0] ? 0 : (this.volume[0] = this.envConstVolume[0] ? this.envelopeValue[0] : this.envelopeCounter[0]);
        int n2 = this.lengthctr[1] <= 0 || this.sweepsilence[1] ? 0 : (this.volume[1] = this.envConstVolume[1] ? this.envelopeValue[1] : this.envelopeCounter[1]);
        this.volume[3] = this.lengthctr[3] <= 0 ? 0 : (this.envConstVolume[3] ? this.envelopeValue[3] : this.envelopeCounter[3]);
    }

    private void clockdmc() {
        if (this.dmcBufferEmpty && this.dmcsamplesleft > 0) {
            this.dmcfillbuffer();
        }
        this.dmcpos = (this.dmcpos + 1) % this.dmcrate;
        if (this.dmcpos == 0) {
            if (this.dmcbitsleft <= 0) {
                this.dmcbitsleft = 8;
                if (this.dmcBufferEmpty) {
                    this.dmcsilence = true;
                } else {
                    this.dmcsilence = false;
                    this.dmcshiftregister = this.dmcbuffer;
                    this.dmcBufferEmpty = true;
                }
            }
            if (!this.dmcsilence) {
                this.dmcvalue += (this.dmcshiftregister & 1) != 0 ? 2 : -2;
                if (this.dmcvalue > 127) {
                    this.dmcvalue = 127;
                }
                if (this.dmcvalue < 0) {
                    this.dmcvalue = 0;
                }
                this.dmcshiftregister >>= 1;
                --this.dmcbitsleft;
            }
        }
    }

    private void dmcfillbuffer() {
        if (this.dmcsamplesleft > 0) {
            this.dmcbuffer = this.cpuram.read(this.dmcaddr++);
            this.dmcBufferEmpty = false;
            this.cpu.stealcycles(4);
            if (this.dmcaddr > 65535) {
                this.dmcaddr = 32768;
            }
            --this.dmcsamplesleft;
            if (this.dmcsamplesleft == 0) {
                if (this.dmcloop) {
                    this.restartdmc();
                } else if (this.dmcirq && !this.statusdmcint) {
                    ++this.cpu.interrupt;
                    this.statusdmcint = true;
                }
            }
        } else {
            this.dmcsilence = true;
        }
    }

    private void restartdmc() {
        this.dmcaddr = this.dmcstartaddr;
        this.dmcsamplesleft = this.dmcsamplelength;
        this.dmcsilence = false;
    }

    private void setlength() {
        for (int i = 0; i < 4; ++i) {
            if (this.lenctrHalt[i] || this.lengthctr[i] <= 0) continue;
            int n = i;
            this.lengthctr[n] = this.lengthctr[n] - 1;
            if (this.lengthctr[i] != 0) continue;
            this.setvolumes();
        }
    }

    private void setlinctr() {
        if (this.linctrflag) {
            this.linearctr = this.linctrreload;
        } else if (this.linearctr > 0) {
            --this.linearctr;
        }
        if (!this.lenctrHalt[2]) {
            this.linctrflag = false;
        }
    }

    private void setenvelope() {
        for (int i = 0; i < 4; ++i) {
            if (this.envelopeStartFlag[i]) {
                this.envelopeStartFlag[i] = false;
                this.envelopePos[i] = this.envelopeValue[i] + 1;
                this.envelopeCounter[i] = 15;
            } else {
                int n = i;
                this.envelopePos[n] = this.envelopePos[n] - 1;
            }
            if (this.envelopePos[i] > 0) continue;
            this.envelopePos[i] = this.envelopeValue[i] + 1;
            if (this.envelopeCounter[i] > 0) {
                int n = i;
                this.envelopeCounter[n] = this.envelopeCounter[n] - 1;
                continue;
            }
            if (!this.lenctrHalt[i] || this.envelopeCounter[i] > 0) continue;
            this.envelopeCounter[i] = 15;
        }
    }

    private void setsweep() {
        for (int i = 0; i < 2; ++i) {
            this.sweepsilence[i] = false;
            if (this.sweepreload[i]) {
                this.sweepreload[i] = false;
                this.sweeppos[i] = this.sweepperiod[i];
            }
            int n = i;
            this.sweeppos[n] = this.sweeppos[n] + 1;
            int n2 = this.timers[i].getperiod() >> 1;
            int n3 = n2 >> this.sweepshift[i];
            if (this.sweepnegate[i]) {
                n3 = -n3 + i;
            }
            if (n2 < 8 || (n3 += n2) > 2047) {
                this.sweepsilence[i] = true;
                continue;
            }
            if (!this.sweepenable[i] || this.sweepshift[i] == 0 || this.lengthctr[i] <= 0 || this.sweeppos[i] <= this.sweepperiod[i]) continue;
            this.sweeppos[i] = 0;
            this.timers[i].setperiod(n3 << 1);
        }
    }
}

