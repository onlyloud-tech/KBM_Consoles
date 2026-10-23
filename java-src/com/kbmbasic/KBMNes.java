// Copyright (c) 2026 Grymlock.
// SPDX-License-Identifier: GPL-3.0-only
/*
 * Decompiled with CFR 0.152.
 */
package com.kbmbasic;

import com.kbmbasic.KBMFrameTexture;
import com.vtbasic.nes.VTINesEngine;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import me.zed_0xff.zombie_buddy.Exposer;

@Exposer.LuaClass(name="KBMNes")
public final class KBMNes {
    private static final String BRIDGE_VERSION = "KBMNes-bridge-2026-04-24-skeleton";
    private static VTINesEngine engine;
    private static String lastError;

    private KBMNes() {
    }

    public static String getBridgeVersion() {
        return BRIDGE_VERSION;
    }

    public static int getWidth() {
        return 256;
    }

    public static int getHeight() {
        return 240;
    }

    public static int btnA() {
        return 1;
    }

    public static int btnB() {
        return 2;
    }

    public static int btnSelect() {
        return 4;
    }

    public static int btnStart() {
        return 8;
    }

    public static int btnUp() {
        return 16;
    }

    public static int btnDown() {
        return 32;
    }

    public static int btnLeft() {
        return 64;
    }

    public static int btnRight() {
        return 128;
    }

    public static boolean init() {
        try {
            if (engine == null) {
                engine = new VTINesEngine();
            }
            lastError = "";
            return true;
        }
        catch (Throwable throwable) {
            lastError = KBMNes.summarize(throwable);
            System.err.println("[KBMNes] init failed: " + lastError);
            return false;
        }
    }

    public static boolean isReady() {
        return engine != null;
    }

    public static boolean isRomLoaded() {
        return engine != null && engine.isRomLoaded();
    }

    public static String getRomPath() {
        return engine != null ? engine.getRomPath() : null;
    }

    public static boolean loadROM(String string) {
        if (engine == null && !KBMNes.init()) {
            return false;
        }
        String string2 = KBMNes.validateRomFile(string);
        if (string2 != null) {
            lastError = "rejected: " + string2;
            System.err.println("[KBMNes] loadROM rejected " + string + " (" + string2 + ")");
            return false;
        }
        try {
            boolean bl = engine.loadROM(string);
            if (!bl) {
                lastError = "loadROM returned false";
            }
            return bl;
        }
        catch (Throwable throwable) {
            lastError = KBMNes.summarize(throwable);
            System.err.println("[KBMNes] loadROM failed: " + lastError);
            return false;
        }
    }

    private static String confineToRomDir(String string) {
        if (string == null || string.isEmpty()) {
            return "empty path";
        }
        try {
            File file = new File(string).getCanonicalFile();
            String string2 = KBMNes.getRomDir();
            if (string2 == null || string2.isEmpty()) {
                return "rom dir unavailable";
            }
            File file2 = new File(string2).getCanonicalFile();
            File file3 = file.getParentFile();
            if (file3 == null || !file3.equals(file2) && !KBMNes.isBundledRomParent(file3, "nes")) {
                return "path outside rom directory or packaged media/roms/nes";
            }
            String string3 = file.getName().toLowerCase();
            if (!string3.endsWith(".nes")) {
                return "extension not allowed (must be .nes)";
            }
            return null;
        }
        catch (Throwable throwable) {
            return "path resolution failed: " + throwable.getClass().getSimpleName();
        }
    }

    private static boolean isBundledRomParent(File file, String string) throws IOException {
        String string2 = file.getCanonicalPath().replace('\\', '/').toLowerCase();
        return string2.endsWith("/media/roms/" + string);
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public static String validateRomFile(String string) {
        int n;
        int n2;
        String string2 = KBMNes.confineToRomDir(string);
        if (string2 != null) {
            return string2;
        }
        File file = new File(string);
        if (!file.isFile()) {
            return "not a file";
        }
        long l = file.length();
        if (l < 16L) {
            return "too small to contain iNES header";
        }
        if (l > 0x400000L) {
            return "file too large (>4 MiB)";
        }
        byte[] byArray = new byte[16];
        try (FileInputStream fileInputStream = new FileInputStream(file);){
            for (n = 0; n < 16 && (n2 = fileInputStream.read(byArray, n, 16 - n)) >= 0; n += n2) {
            }
            if (n < 16) {
                String string3 = "could not read 16-byte header";
                return string3;
            }
        }
        catch (Throwable throwable) {
            return "read error: " + throwable.getClass().getSimpleName();
        }
        if (byArray[0] != 78) return "missing iNES magic (not a valid .nes file)";
        if (byArray[1] != 69) return "missing iNES magic (not a valid .nes file)";
        if (byArray[2] != 83) return "missing iNES magic (not a valid .nes file)";
        if (byArray[3] != 26) {
            return "missing iNES magic (not a valid .nes file)";
        }
        int n3 = byArray[4] & 0xFF;
        n = byArray[5] & 0xFF;
        int n4 = n2 = (byArray[6] & 4) != 0 ? 1 : 0;
        if (n3 == 0) {
            return "header declares 0 PRG banks";
        }
        long l2 = 16L + (n2 != 0 ? 512L : 0L) + (long)n3 * 16L * 1024L + (long)n * 8L * 1024L;
        if (l >= l2) return null;
        return "file truncated (have " + l + " bytes, header expects " + l2 + ")";
    }

    public static String validateRom(String string) {
        String string2 = KBMNes.validateRomFile(string);
        return string2 == null ? "" : string2;
    }

    public static void runFrame() {
        if (engine == null) {
            return;
        }
        try {
            engine.runFrame();
        }
        catch (Throwable throwable) {
            lastError = KBMNes.summarize(throwable);
        }
    }

    public static String probeAudio() {
        try {
            AudioFormat audioFormat = new AudioFormat(44100.0f, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                return "fail: format not supported";
            }
            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
            sourceDataLine.open(audioFormat, 8192);
            sourceDataLine.start();
            byte[] byArray = new byte[1024];
            sourceDataLine.write(byArray, 0, byArray.length);
            sourceDataLine.drain();
            sourceDataLine.stop();
            sourceDataLine.close();
            return "ok";
        }
        catch (Throwable throwable) {
            return "fail: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        }
    }

    public static String testTone() {
        try {
            float f = 44100.0f;
            AudioFormat audioFormat = new AudioFormat(f, 16, 2, true, false);
            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
            sourceDataLine.open(audioFormat, 16384);
            sourceDataLine.start();
            int n = (int)((double)f * 0.25);
            byte[] byArray = new byte[n * 4];
            for (int i = 0; i < n; ++i) {
                double d = (double)i / (double)f;
                int n2 = (int)(Math.sin(2764.601535159018 * d) * 12000.0);
                byArray[i * 4] = (byte)(n2 & 0xFF);
                byArray[i * 4 + 1] = (byte)(n2 >> 8 & 0xFF);
                byArray[i * 4 + 2] = (byte)(n2 & 0xFF);
                byArray[i * 4 + 3] = (byte)(n2 >> 8 & 0xFF);
            }
            sourceDataLine.write(byArray, 0, byArray.length);
            sourceDataLine.drain();
            sourceDataLine.stop();
            sourceDataLine.close();
            return "ok (played 440 Hz tone)";
        }
        catch (Throwable throwable) {
            return "fail: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        }
    }

    public static int[] getPixels() {
        if (engine == null && !KBMNes.init()) {
            return new int[61440];
        }
        return engine.getPixels();
    }

    public static Object getFrameTexture() {
        if (engine == null && !KBMNes.init()) {
            return null;
        }
        int[] nArray = engine.getPixels();
        if (nArray == null) {
            return null;
        }
        return KBMFrameTexture.update("nes", nArray, 256, 240);
    }

    public static String getFrameTextureStatus() {
        return KBMFrameTexture.getStatus("nes");
    }

    public static String getFrameTextureInfo() {
        return KBMFrameTexture.getInfo("nes");
    }

    public static String getRowRLE(int n) {
        int n2;
        if (engine == null) {
            return "";
        }
        int[] nArray = engine.getPixels();
        if (nArray == null) {
            return "";
        }
        int n3 = 256;
        int n4 = 240;
        if (n < 0 || n >= n4) {
            return "";
        }
        int n5 = n * n3;
        int n6 = n5 + n3;
        if (n6 > nArray.length) {
            n6 = nArray.length;
        }
        if ((n2 = n6 - n5) <= 0) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder(n2 * 6);
        int n7 = 0;
        int n8 = nArray[n5] & 0xFFFFFF;
        for (int i = 1; i <= n2; ++i) {
            int n9;
            int n10 = n9 = i < n2 ? nArray[n5 + i] & 0xFFFFFF : -1;
            if (n9 == n8) continue;
            stringBuilder.append(n7).append(',').append(i - n7).append(',').append(n8).append(';');
            n7 = i;
            n8 = n9;
        }
        return stringBuilder.toString();
    }

    public static String getRegionRLE(int n, int n2, int n3, int n4, int n5) {
        if (engine == null) {
            return "";
        }
        int[] nArray = engine.getPixels();
        if (nArray == null) {
            return "";
        }
        if (n5 < 1) {
            n5 = 1;
        }
        int n6 = 256;
        int n7 = 240;
        if (n < 0) {
            n = 0;
        }
        if (n2 > n7) {
            n2 = n7;
        }
        if (n2 <= n) {
            return "";
        }
        int n8 = n6 / n5;
        if (n8 < 1) {
            n8 = 1;
        }
        HashMap<String, int[]> hashMap = new HashMap<String, int[]>();
        StringBuilder stringBuilder = new StringBuilder(2048);
        int n9 = -1;
        for (int i = n; i < n2; i += n5) {
            ++n9;
            int n10 = i * n6;
            int n11 = n4 + (i - n);
            HashSet<String> hashSet = new HashSet<String>();
            int n12 = 0;
            int n13 = nArray[n10] & 0xFFFFFF;
            for (int j = 1; j <= n8; ++j) {
                int n14;
                int n15;
                if (j < n8) {
                    n15 = j * n5;
                    if (n15 >= n6) {
                        n15 = n6 - 1;
                    }
                    n14 = nArray[n10 + n15] & 0xFFFFFF;
                } else {
                    n14 = -1;
                }
                if (n14 == n13) continue;
                n15 = n3 + n12 * n5;
                int n16 = (j - n12) * n5;
                String string = n15 + "|" + n16 + "|" + n13;
                int[] nArray2 = (int[])hashMap.get(string);
                if (nArray2 != null && nArray2[5] == n9 - 1) {
                    nArray2[3] = nArray2[3] + n5;
                    nArray2[5] = n9;
                } else {
                    if (nArray2 != null) {
                        stringBuilder.append(nArray2[0]).append(',').append(nArray2[1]).append(',').append(nArray2[2]).append(',').append(nArray2[3]).append(',').append(nArray2[4]).append(',');
                        hashMap.remove(string);
                    }
                    int[] nArray3 = new int[]{n15, n11, n16, n5, n13, n9};
                    hashMap.put(string, nArray3);
                }
                hashSet.add(string);
                n12 = j;
                n13 = n14;
            }
            Iterator<Map.Entry<String, int[]>> iterator = hashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, int[]> entry = iterator.next();
                if (hashSet.contains(entry.getKey())) continue;
                int[] nArray4 = (int[])entry.getValue();
                stringBuilder.append(nArray4[0]).append(',').append(nArray4[1]).append(',').append(nArray4[2]).append(',').append(nArray4[3]).append(',').append(nArray4[4]).append(',');
                iterator.remove();
            }
        }
        for (int[] nArray5 : hashMap.values()) {
            stringBuilder.append(nArray5[0]).append(',').append(nArray5[1]).append(',').append(nArray5[2]).append(',').append(nArray5[3]).append(',').append(nArray5[4]).append(',');
        }
        return stringBuilder.toString();
    }

    public static String getRegionRLE2(int n, int n2, int n3, int n4, int n5, int n6) {
        if (engine == null) {
            return "";
        }
        int[] nArray = engine.getPixels();
        if (nArray == null) {
            return "";
        }
        if (n5 < 1) {
            n5 = 1;
        }
        if (n6 < 1) {
            n6 = 1;
        }
        int n7 = 256;
        int n8 = 240;
        if (n < 0) {
            n = 0;
        }
        if (n2 > n8) {
            n2 = n8;
        }
        if (n2 <= n) {
            return "";
        }
        int n9 = n7 / n5;
        if (n9 < 1) {
            n9 = 1;
        }
        HashMap<String, int[]> hashMap = new HashMap<String, int[]>();
        StringBuilder stringBuilder = new StringBuilder(2048);
        int n10 = -1;
        for (int i = n; i < n2; i += n5) {
            int n11 = i * n7;
            int n12 = n4 + ++n10 * n6;
            HashSet<String> hashSet = new HashSet<String>();
            int n13 = 0;
            int n14 = nArray[n11] & 0xFFFFFF;
            for (int j = 1; j <= n9; ++j) {
                int n15;
                int n16;
                if (j < n9) {
                    n16 = j * n5;
                    if (n16 >= n7) {
                        n16 = n7 - 1;
                    }
                    n15 = nArray[n11 + n16] & 0xFFFFFF;
                } else {
                    n15 = -1;
                }
                if (n15 == n14) continue;
                n16 = n3 + n13 * n6;
                int n17 = (j - n13) * n6;
                String string = n16 + "|" + n17 + "|" + n14;
                int[] nArray2 = (int[])hashMap.get(string);
                if (nArray2 != null && nArray2[5] == n10 - 1) {
                    nArray2[3] = nArray2[3] + n6;
                    nArray2[5] = n10;
                } else {
                    if (nArray2 != null) {
                        stringBuilder.append(nArray2[0]).append(',').append(nArray2[1]).append(',').append(nArray2[2]).append(',').append(nArray2[3]).append(',').append(nArray2[4]).append(',');
                        hashMap.remove(string);
                    }
                    int[] nArray3 = new int[]{n16, n12, n17, n6, n14, n10};
                    hashMap.put(string, nArray3);
                }
                hashSet.add(string);
                n13 = j;
                n14 = n15;
            }
            Iterator<Map.Entry<String, int[]>> iterator = hashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, int[]> entry = iterator.next();
                if (hashSet.contains(entry.getKey())) continue;
                int[] nArray4 = (int[])entry.getValue();
                stringBuilder.append(nArray4[0]).append(',').append(nArray4[1]).append(',').append(nArray4[2]).append(',').append(nArray4[3]).append(',').append(nArray4[4]).append(',');
                iterator.remove();
            }
        }
        for (int[] nArray5 : hashMap.values()) {
            stringBuilder.append(nArray5[0]).append(',').append(nArray5[1]).append(',').append(nArray5[2]).append(',').append(nArray5[3]).append(',').append(nArray5[4]).append(',');
        }
        return stringBuilder.toString();
    }

    public static void setButton(int n, int n2, boolean bl) {
        if (engine == null) {
            return;
        }
        engine.setButton(n, n2, bl);
    }

    public static void setPadMask(int n, int n2) {
        if (engine == null) {
            return;
        }
        engine.setPadMask(n, n2);
    }

    public static void reset() {
        if (engine != null) {
            engine.reset();
        }
    }

    public static void pauseAudio() {
        if (engine != null) {
            try {
                engine.pauseAudio();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    public static void destroy() {
        if (engine != null) {
            try {
                engine.pauseAudio();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            try {
                engine.destroy();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            engine = null;
        }
    }

    public static String getLastError() {
        return lastError;
    }

    public static String getRomDir() {
        String string = System.getProperty("user.home", ".");
        File file = new File(string, "Zomboid" + File.separator + "kbm_nes_roms");
        return file.getAbsolutePath();
    }

    public static String listRoms() {
        try {
            File file2 = new File(KBMNes.getRomDir());
            if (!file2.isDirectory()) {
                return "";
            }
            File[] fileArray = file2.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".nes"));
            if (fileArray == null || fileArray.length == 0) {
                return "";
            }
            Arrays.sort(fileArray, Comparator.comparing(file -> file.getName().toLowerCase()));
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < fileArray.length; ++i) {
                if (i > 0) {
                    stringBuilder.append('\n');
                }
                stringBuilder.append(fileArray[i].getName());
            }
            return stringBuilder.toString();
        }
        catch (Throwable throwable) {
            lastError = KBMNes.summarize(throwable);
            return "";
        }
    }

    public static String resolveRomPath(String string) {
        if (string == null || string.isEmpty()) {
            return "";
        }
        return new File(KBMNes.getRomDir(), string).getAbsolutePath();
    }

    private static String summarize(Throwable throwable) {
        if (throwable == null) {
            return "null";
        }
        StringBuilder stringBuilder = new StringBuilder();
        Throwable throwable2 = throwable;
        for (int n = 0; throwable2 != null && n < 4; throwable2 = throwable2.getCause(), ++n) {
            if (n > 0) {
                stringBuilder.append(" <- ");
            }
            stringBuilder.append(throwable2.getClass().getSimpleName()).append(": ").append(throwable2.getMessage());
            StackTraceElement[] stackTraceElementArray = throwable2.getStackTrace();
            if (stackTraceElementArray == null || stackTraceElementArray.length <= 0) continue;
            stringBuilder.append(" @ ").append(stackTraceElementArray[0]);
        }
        return stringBuilder.toString();
    }

    static {
        lastError = "";
    }
}
