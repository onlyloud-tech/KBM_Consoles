/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.audio.ExpansionSoundChip;

public class FDSSoundChip
implements ExpansionSoundChip {
    boolean regEnable = true;
    int[] wavetable = new int[64];
    int waveAddr;
    int waveOut;
    int waveAccum;
    boolean waveWriteEnable;
    boolean volEnvDirection;
    boolean volEnvDisable;
    boolean modEnvDirection;
    boolean modEnvDisable;
    int volEnvSpeed;
    int modEnvSpeed;
    int envClockMultiplier = 232;
    int pitch;
    boolean modDisable;
    int modCtr;
    int modFreq;
    int modAccum;
    int[] modTable = new int[64];
    int modTableAddr;
    int masterVol;
    int volGain;
    int modGain;
    int lpaccum;
    int modout;
    boolean BothEnvDisable;
    boolean haltWaveAndReset;
    int modEnvAccum;
    int volEnvAccum;

    @Override
    public void clock(int n) {
        for (int i = 0; i < n; ++i) {
            this.runUnits();
        }
    }

    private void runUnits() {
        if (this.pitch + this.modout > 0 && !this.haltWaveAndReset) {
            this.waveAccum += this.pitch + this.modout;
            if ((this.waveAccum & 0xFFFF) != this.waveAccum) {
                this.waveAccum &= 0xFFFF;
                this.waveAddr = this.waveAddr + 1 & 0x3F;
            }
        }
        if (this.modFreq > 0 && !this.modDisable) {
            this.modAccum += this.modFreq;
            if ((this.modAccum & 0xFFFF) != this.modAccum) {
                this.modAccum &= 0xFFFF;
                this.CalculateModulator();
            }
        } else if (this.modDisable) {
            this.modAccum = 0;
            this.modout = 0;
        }
        if (!this.haltWaveAndReset && !this.BothEnvDisable && this.envClockMultiplier != 0) {
            this.CalculateEnvelopes();
        }
        if (!this.waveWriteEnable) {
            this.waveOut = this.wavetable[this.waveAddr];
        }
        int n = this.volGain > 32 ? 32 : this.volGain;
        int n2 = this.waveOut * n;
        switch (this.masterVol) {
            default: {
                n2 *= 8;
                break;
            }
            case 1: {
                n2 *= 5;
                break;
            }
            case 2: {
                n2 *= 4;
                break;
            }
            case 3: {
                n2 *= 3;
            }
        }
        this.lpaccum -= (n2 += this.lpaccum) >> 6;
    }

    private void CalculateModulator() {
        switch (this.modTable[this.modTableAddr]) {
            default: {
                this.modCtr += 0;
                break;
            }
            case 1: {
                ++this.modCtr;
                break;
            }
            case 2: {
                this.modCtr += 2;
                break;
            }
            case 3: {
                this.modCtr += 4;
                break;
            }
            case 4: {
                this.modCtr = 0;
                break;
            }
            case 5: {
                this.modCtr -= 4;
                break;
            }
            case 6: {
                this.modCtr -= 2;
                break;
            }
            case 7: {
                --this.modCtr;
            }
        }
        ++this.modTableAddr;
        this.modTableAddr &= 0x3F;
        this.modCtr = this.modCtr << 25 >> 25;
        int n = this.modCtr * this.modGain;
        int n2 = n & 0xF;
        if (n2 > 0 && ((n >>= 4) & 0x80) == 0) {
            n = this.modCtr < 0 ? --n : (n += 2);
        }
        if (n >= 192) {
            n -= 256;
        } else if (n < -64) {
            n += 256;
        }
        n = this.pitch * n;
        n2 = n & 0x3F;
        n >>= 6;
        if (n2 >= 32) {
            ++n;
        }
        this.modout = n;
    }

    private void CalculateEnvelopes() {
        if (!this.modEnvDisable) {
            ++this.modEnvAccum;
            if (this.modEnvAccum > 8 * this.envClockMultiplier * (this.modEnvSpeed + 1)) {
                this.modEnvAccum = 0;
                if (this.modEnvDirection) {
                    if (this.modGain < 32) {
                        ++this.modGain;
                    }
                } else if (this.modGain > 0) {
                    --this.modGain;
                }
            }
        }
        if (!this.volEnvDisable) {
            ++this.volEnvAccum;
            if (this.volEnvAccum > 8 * this.envClockMultiplier * (this.volEnvSpeed + 1)) {
                this.volEnvAccum = 0;
                if (this.volEnvDirection) {
                    if (this.volGain < 32) {
                        ++this.volGain;
                    }
                } else if (this.volGain > 0) {
                    --this.volGain;
                }
            }
        }
    }

    @Override
    public void write(int n, int n2) {
        if (n == 16419) {
            boolean bl = this.regEnable = (n2 & 1) != 0;
        }
        if (this.regEnable) {
            if (n >= 16448 && n <= 16511) {
                if (this.waveWriteEnable) {
                    this.wavetable[n - 16448 & 0x3F] = n2 & 0x3F;
                }
            } else if (n == 16512) {
                this.volEnvDisable = (n2 & 0x80) != 0;
                boolean bl = this.volEnvDirection = (n2 & 0x40) != 0;
                if (this.volEnvDisable) {
                    this.volGain = n2 & 0x3F;
                }
                this.volEnvSpeed = n2 & 0x3F;
                this.volEnvAccum = 0;
            } else if (n == 16514) {
                this.pitch &= 0xF00;
                this.pitch |= n2 & 0xFF;
            } else if (n == 16515) {
                this.pitch &= 0xFF;
                this.pitch |= (n2 & 0xF) << 8;
                boolean bl = this.haltWaveAndReset = (n2 & 0x80) != 0;
                if (this.haltWaveAndReset) {
                    this.waveAccum = 0;
                    this.waveAddr = 0;
                }
                this.BothEnvDisable = (n2 & 0x40) != 0;
            } else if (n == 16516) {
                this.modEnvDisable = (n2 & 0x80) != 0;
                boolean bl = this.modEnvDirection = (n2 & 0x40) != 0;
                if (this.modEnvDisable) {
                    this.modGain = n2 & 0x3F;
                }
                this.modEnvSpeed = n2 & 0x3F;
                this.modAccum = 0;
                this.modEnvAccum = 0;
            } else if (n == 16517) {
                this.modCtr = (n2 & 0x7F) << 25 >> 25;
            } else if (n == 16518) {
                this.modFreq &= 0xF00;
                this.modFreq |= n2 & 0xFF;
            } else if (n == 16519) {
                this.modFreq &= 0xFF;
                this.modFreq |= (n2 & 0xF) << 8;
                this.modDisable = (n2 & 0x80) != 0;
            } else if (n == 16520) {
                if (this.modDisable) {
                    for (int i = 0; i < 2; ++i) {
                        this.modTable[this.modTableAddr] = n2 & 7;
                        this.modTableAddr = this.modTableAddr + 1 & 0x3F;
                    }
                }
                this.modAccum = 0;
            } else if (n == 16521) {
                this.masterVol = n2 & 3;
                this.waveWriteEnable = (n2 & 0x80) != 0;
            } else if (n == 16522) {
                this.envClockMultiplier = n2;
            }
        }
    }

    public int read(int n) {
        if (n >= 16448 && n < 16512) {
            return this.wavetable[n - 16448] | 0x40;
        }
        if (n == 16528) {
            return this.volGain;
        }
        if (n == 16530) {
            return this.modGain;
        }
        return 64;
    }

    @Override
    public int getval() {
        return this.lpaccum;
    }
}

