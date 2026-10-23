/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.NES;
import com.grapeshot.halfnes.PrefsSingleton;
import com.grapeshot.halfnes.audio.AudioOutInterface;
import com.grapeshot.halfnes.mappers.Mapper;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class SwingAudioImpl
implements AudioOutInterface {
    private boolean soundEnable = PrefsSingleton.get().getBoolean("soundEnable", true);
    private SourceDataLine sdl;
    private byte[] audiobuf;
    private int bufptr = 0;
    private float outputvol = (float)((double)PrefsSingleton.get().getInt("outputvol", 13107) / 16384.0);

    public SwingAudioImpl(NES nES, int n, Mapper.TVType tVType) {
        double d = switch (tVType) {
            default -> 60.0;
            case PAL, DENDY -> 50.0;
        };
        if (this.soundEnable) {
            int n2 = (int)Math.ceil((double)(n * 2) / d);
            this.audiobuf = new byte[n2 * 2];
            try {
                AudioFormat audioFormat = new AudioFormat(n, 16, 2, true, false);
                this.sdl = AudioSystem.getSourceDataLine(audioFormat);
                this.sdl.open(audioFormat, n2 * 4 * 2 * 2);
                this.sdl.start();
            }
            catch (LineUnavailableException lineUnavailableException) {
                System.err.println(lineUnavailableException);
                nES.messageBox("Unable to inintialize sound.");
                this.soundEnable = false;
            }
            catch (IllegalArgumentException illegalArgumentException) {
                System.err.println(illegalArgumentException);
                nES.messageBox("Unable to inintialize sound.");
                this.soundEnable = false;
            }
        }
    }

    @Override
    public final void flushFrame(boolean bl) {
        if (this.soundEnable) {
            if (this.sdl.available() < this.bufptr) {
                if (bl) {
                    this.sdl.write(this.audiobuf, 0, this.bufptr);
                }
            } else {
                this.sdl.write(this.audiobuf, 0, this.bufptr);
            }
        }
        this.bufptr = 0;
    }

    @Override
    public final void outputSample(int n) {
        if (this.soundEnable) {
            if ((n = (int)((float)n * this.outputvol)) < Short.MIN_VALUE) {
                n = Short.MIN_VALUE;
            }
            if (n > Short.MAX_VALUE) {
                n = Short.MAX_VALUE;
            }
            int n2 = n;
            this.audiobuf[this.bufptr] = (byte)(n2 & 0xFF);
            this.audiobuf[this.bufptr + 1] = (byte)(n2 >> 8 & 0xFF);
            int n3 = n;
            this.audiobuf[this.bufptr + 2] = (byte)(n3 & 0xFF);
            this.audiobuf[this.bufptr + 3] = (byte)(n3 >> 8 & 0xFF);
            this.bufptr += 4;
        }
    }

    @Override
    public void pause() {
        if (this.soundEnable) {
            try {
                int n = Math.max(64, this.sdl.getBufferSize() / 8);
                byte[] byArray = new byte[n * 4];
                if (this.sdl.available() >= byArray.length) {
                    this.sdl.write(byArray, 0, byArray.length);
                }
                this.sdl.drain();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            this.sdl.stop();
        }
    }

    @Override
    public void resume() {
        if (this.soundEnable) {
            this.sdl.start();
        }
    }

    @Override
    public final void destroy() {
        if (this.soundEnable) {
            try {
                int n = Math.max(64, this.sdl.getBufferSize() / 8);
                byte[] byArray = new byte[n * 4];
                if (this.sdl.available() >= byArray.length) {
                    this.sdl.write(byArray, 0, byArray.length);
                }
                this.sdl.drain();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            this.sdl.stop();
            this.sdl.close();
        }
    }

    @Override
    public final boolean bufferHasLessThan(int n) {
        return this.sdl == null ? false : this.sdl.getBufferSize() - this.sdl.available() <= n;
    }
}

