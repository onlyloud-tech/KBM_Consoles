/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.ui;

import com.grapeshot.halfnes.NES;
import com.grapeshot.halfnes.PrefsSingleton;
import com.grapeshot.halfnes.ui.FrameLimiterInterface;

public class FrameLimiterImpl
implements FrameLimiterInterface {
    NES nes;
    private long sleepingtest = 0L;
    public long FRAME_NS;

    public FrameLimiterImpl(NES nES, long l) {
        this.nes = nES;
        this.FRAME_NS = l;
    }

    @Override
    public void setInterval(long l) {
        this.FRAME_NS = l;
    }

    @Override
    public void sleep() {
        if (!PrefsSingleton.get().getBoolean("Sleep", true)) {
            return;
        }
        long l = System.nanoTime() - this.nes.frameStartTime;
        if (l < this.FRAME_NS) {
            long l2 = this.FRAME_NS - l + this.sleepingtest;
            if (l2 < 0L) {
                return;
            }
            this.sleepingtest = System.nanoTime();
            try {
                Thread.sleep(l2 / 1000000L);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            this.sleepingtest = System.nanoTime() - this.sleepingtest;
            this.sleepingtest = l2 - this.sleepingtest;
        }
    }

    @Override
    public void sleepFixed() {
        try {
            Thread.sleep(16L);
        }
        catch (InterruptedException interruptedException) {
            // empty catch block
        }
    }

    public static void forceHighResolutionTimer() {
        Thread thread = new Thread("ForceHighResolutionTimer"){

            @Override
            public void run() {
                try {
                    while (true) {
                        Thread.sleep(99999999999L);
                    }
                }
                catch (InterruptedException interruptedException) {
                    return;
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }
}

