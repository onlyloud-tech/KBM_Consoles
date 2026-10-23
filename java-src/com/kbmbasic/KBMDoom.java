// Copyright (c) 2026 Grymlock.
// SPDX-License-Identifier: GPL-3.0-only
/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.kbmbasic.KBMFrameTexture
 *  com.vtbasic.doom.VTIDoomEngine
 *  me.zed_0xff.zombie_buddy.Exposer$LuaClass
 */
package com.kbmbasic;

import com.kbmbasic.KBMFrameTexture;
import com.vtbasic.doom.VTIDoomEngine;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import me.zed_0xff.zombie_buddy.Exposer;

@Exposer.LuaClass(name="KBMDoom")
public final class KBMDoom {
    private static final String BRIDGE_VERSION = "KBMDoom-bridge-2026-05-02-frontend-v1";
    private static final String BRIDGE_CAPABILITIES = "core_rle,blit_opt_in,texture_probe_v1,run_texture_probe,blit_diag,frontend_v1";
    private static volatile boolean tickFatal = false;
    private static volatile String tickFatalReason = "";
    private static VTIDoomEngine engine;
    private static volatile int[] pixelBuffer;
    private static int quantizationBits;
    private static int displayHz;
    private static boolean gameplayBlitRequested;
    private static Object gameplayTexture;
    private static ByteBuffer gameplayTextureBuffer;
    private static int gameplayTextureW;
    private static int gameplayTextureH;
    private static long gameplayTextureFrame;
    private static Object testTexture;
    private static ByteBuffer testTextureBuffer;
    private static String testTextureProbeStatus;
    private static String testTextureProbeError;
    private static String blitStatus;
    private static String blitError;
    private static boolean blitPermanentlyDisabled;
    private static int gameplayTexturePotW;
    private static int gameplayTexturePotH;

    private KBMDoom() {
    }

    private static String summarizeThrowable(Throwable throwable) {
        if (throwable == null) {
            return "null";
        }
        StringBuilder stringBuilder = new StringBuilder();
        Throwable throwable2 = throwable;
        for (int n = 0; throwable2 != null && n < 5; throwable2 = throwable2.getCause(), ++n) {
            if (n > 0) {
                stringBuilder.append(" | caused by: ");
            }
            stringBuilder.append(throwable2.getClass().getName());
            stringBuilder.append(": ");
            stringBuilder.append(throwable2.getMessage());
            StackTraceElement[] stackTraceElementArray = throwable2.getStackTrace();
            if (stackTraceElementArray == null || stackTraceElementArray.length <= 0) continue;
            stringBuilder.append(" @ ").append(stackTraceElementArray[0].toString());
        }
        return stringBuilder.toString();
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static String validateWadFile(String string) {
        int n;
        File file;
        if (string == null) return "empty path";
        if (string.isEmpty()) {
            return "empty path";
        }
        try {
            file = new File(string).getCanonicalFile();
        }
        catch (Throwable throwable) {
            return "path resolution failed: " + throwable.getClass().getSimpleName();
        }
        if (!KBMDoom.isWithinPackagedWadRoot(file)) {
            return "outside packaged WAD directory";
        }
        String string2 = file.getName().toLowerCase();
        if (!string2.endsWith(".wad")) {
            return "extension not allowed (must be .wad)";
        }
        if (!file.exists()) {
            return "file not found";
        }
        if (!file.isFile()) {
            return "not a regular file";
        }
        long l = file.length();
        if (l < 4096L) {
            return "file too small (< 4 KiB)";
        }
        if (l > 0x10000000L) {
            return "file too large (> 256 MiB)";
        }
        byte[] byArray = new byte[4];
        try (FileInputStream fileInputStream = new FileInputStream(file);){
            int n2;
            for (n = 0; n < 4 && (n2 = fileInputStream.read(byArray, n, 4 - n)) >= 0; n += n2) {
            }
            if (n < 4) {
                String string3 = "could not read 4-byte magic";
                return string3;
            }
        }
        catch (Throwable throwable) {
            return "read error: " + throwable.getClass().getSimpleName();
        }
        boolean bl = byArray[0] == 73 && byArray[1] == 87 && byArray[2] == 65 && byArray[3] == 68;
        n = byArray[0] == 80 && byArray[1] == 87 && byArray[2] == 65 && byArray[3] == 68 ? 1 : 0;
        if (bl) return null;
        if (n != 0) return null;
        return "missing IWAD/PWAD magic (not a valid DOOM WAD)";
    }

    private static boolean isWithinPackagedWadRoot(File file) {
        String string;
        File file2;
        File file3 = file2 = file == null ? null : file.getParentFile();
        if (file2 == null) {
            return false;
        }
        try {
            string = file2.getCanonicalPath().replace('\\', '/').toLowerCase();
        }
        catch (Throwable throwable) {
            return false;
        }
        return string.endsWith("/knoxbox media/common/media/data") || string.endsWith("/knoxbox media/42/media/data") || string.endsWith("/knoxbox media/media/data") || string.endsWith("/kbm_media/common/media/data") || string.endsWith("/kbm_media/42/media/data") || string.endsWith("/kbm_media/media/data");
    }

    public static String validateWad(String string) {
        String string2 = KBMDoom.validateWadFile(string);
        return string2 == null ? "" : string2;
    }

    public static boolean init(String string) throws Exception {
        return KBMDoom.init(string, null);
    }

    public static boolean init(String string, String string2) throws Exception {
        String[] stringArray;
        Object object;
        String string3;
        tickFatal = false;
        tickFatalReason = "";
        String string4 = KBMDoom.validateWadFile(string);
        if (string4 != null) {
            System.err.println("[KBMDoom] init rejected " + string + " (" + string4 + ")");
            throw new IllegalArgumentException("WAD rejected: " + string4);
        }
        String string5 = string3 = string2 == null || string2.isEmpty() ? null : string2;
        if (string3 != null && (object = KBMDoom.validateWadFile(string3)) != null) {
            System.err.println("[KBMDoom] init rejected PWAD " + string3 + " (" + (String)object + ")");
            throw new IllegalArgumentException("PWAD rejected: " + (String)object);
        }
        try {
            object = KBMDoom.class.getClassLoader();
            for (String string6 : stringArray = new String[]{"f.EndLevel$1", "f.EndLevel$endlevel_state", "f.Finale$1", "f.Wiper$Wipe", "w.animenum_t"}) {
                try {
                    Class.forName(string6, false, (ClassLoader)object);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            if (engine == null) {
                engine = new VTIDoomEngine();
            }
            engine.init(string, string3);
            engine.setDisplayHz(displayHz);
            return true;
        }
        catch (InvocationTargetException invocationTargetException) {
            Throwable cause = invocationTargetException.getCause() != null ? invocationTargetException.getCause() : invocationTargetException;
            throw new RuntimeException("headlessInit failed: " + KBMDoom.summarizeThrowable(cause), cause);
        }
        catch (Throwable throwable) {
            throw new RuntimeException("KBMDoom.init failed: " + KBMDoom.summarizeThrowable(throwable), throwable);
        }
    }

    public static boolean supportsFrontend() {
        return true;
    }

    public static boolean initAdvanced(String string, String string2, String string3, Integer n, Integer n2, Integer n3) throws Exception {
        Object object;
        String string4;
        tickFatal = false;
        tickFatalReason = "";
        String string5 = KBMDoom.validateWadFile(string);
        if (string5 != null) {
            System.err.println("[KBMDoom] initAdvanced rejected " + string + " (" + string5 + ")");
            throw new IllegalArgumentException("WAD rejected: " + string5);
        }
        String string6 = string4 = string2 == null || string2.isEmpty() ? null : string2;
        if (string4 != null && (object = KBMDoom.validateWadFile(string4)) != null) {
            System.err.println("[KBMDoom] initAdvanced rejected PWAD " + string4 + " (" + (String)object + ")");
            throw new IllegalArgumentException("PWAD rejected: " + (String)object);
        }
        try {
            String[] stringArray;
            object = KBMDoom.class.getClassLoader();
            for (String string7 : stringArray = new String[]{"f.EndLevel$1", "f.EndLevel$endlevel_state", "f.Finale$1", "f.Wiper$Wipe", "w.animenum_t"}) {
                try {
                    Class.forName(string7, false, (ClassLoader)object);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        object = string3 == null || string3.isEmpty() ? "immediate_game" : string3;
        System.out.println("[KBMDoom] initAdvanced wadId=" + string + " startupMode=" + (String)object);
        try {
            if (engine == null) {
                engine = new VTIDoomEngine();
            }
            engine.initWithMode(string, string4, (String)object);
            engine.setDisplayHz(displayHz);
            return true;
        }
        catch (InvocationTargetException invocationTargetException) {
            Throwable cause = invocationTargetException.getCause() != null ? invocationTargetException.getCause() : invocationTargetException;
            throw new RuntimeException("initAdvanced failed: " + KBMDoom.summarizeThrowable(cause), cause);
        }
        catch (Throwable throwable) {
            throw new RuntimeException("KBMDoom.initAdvanced failed: " + KBMDoom.summarizeThrowable(throwable), throwable);
        }
    }

    public static String getFrontendState() {
        if (engine == null) {
            return "unknown";
        }
        try {
            if (engine.isMenuActive()) {
                return "menu";
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            if (engine.isDemoPlayback()) {
                return "demo";
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        String string = engine.getGameState();
        if (string == null) {
            return "unknown";
        }
        switch (string) {
            case "GS_LEVEL": {
                return "gameplay";
            }
            case "GS_INTERMISSION": {
                return "intermission";
            }
            case "GS_FINALE": {
                return "finale";
            }
            case "GS_DEMOSCREEN": {
                return "title";
            }
        }
        return "unknown";
    }

    public static boolean sendMenuInput(String string) {
        if (engine == null) {
            return false;
        }
        boolean bl = engine.postMenuInput(string);
        if (!bl) {
            System.err.println("[KBMDoom] sendMenuInput failed for action=" + String.valueOf(string));
        }
        return bl;
    }

    public static void restartToTitle() {
        if (engine != null) {
            engine.startTitle();
        }
    }

    public static void tick(int n) throws Exception {
        int[] nArray;
        if (tickFatal) {
            return;
        }
        if (engine != null) {
            try {
                engine.tick(n);
            }
            catch (Throwable throwable) {
                tickFatal = true;
                tickFatalReason = throwable.getClass().getSimpleName() + ": " + (throwable.getMessage() == null ? "" : throwable.getMessage());
                System.err.println("[KBMDoom] tick fatal \u2014 suppressing further engine ticks: " + tickFatalReason);
                throwable.printStackTrace(System.err);
                return;
            }
        }
        if (engine != null && (nArray = engine.getPixels()) != null && nArray.length > 0) {
            int[] nArray2 = new int[nArray.length];
            System.arraycopy(nArray, 0, nArray2, 0, nArray.length);
            pixelBuffer = nArray2;
        }
    }

    public static boolean isTickFatal() {
        return tickFatal;
    }

    public static String getTickFatalReason() {
        return tickFatalReason;
    }

    private static void blitLog(String string) {
        try {
            System.err.println("[VTIDoomBlit] " + string);
            System.err.flush();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private static int nextPow2(int n) {
        int n2;
        for (n2 = 1; n2 < n; n2 <<= 1) {
        }
        return n2;
    }

    private static Object createTextureReflective(int n, int n2) {
        Class<?> clazz;
        KBMDoom.blitLog("createTextureReflective: begin w=" + n + " h=" + n2);
        try {
            clazz = Class.forName("zombie.core.textures.Texture");
            KBMDoom.blitLog("createTextureReflective: Texture class loaded");
        }
        catch (Throwable throwable) {
            blitStatus = "class_not_found";
            blitError = KBMDoom.summarizeThrowable(throwable);
            KBMDoom.blitLog("createTextureReflective: class lookup FAILED: " + blitError);
            return null;
        }
        try {
            KBMDoom.blitLog("createTextureReflective: trying Texture(String,int[]) w*h=" + n * n2);
            int[] nArray = new int[n * n2];
            Arrays.fill(nArray, -16777216);
            String string = "vti_doom_gameplay_" + n + "x" + n2;
            Object obj = clazz.getConstructor(String.class, int[].class).newInstance(string, nArray);
            KBMDoom.blitLog("createTextureReflective: Texture(String,int[]) OK");
            blitStatus = "ctor_strarr_ok";
            return obj;
        }
        catch (NoSuchMethodException noSuchMethodException) {
            KBMDoom.blitLog("createTextureReflective: Texture(String,int[]) not found");
        }
        catch (Throwable throwable) {
            KBMDoom.blitLog("createTextureReflective: Texture(String,int[]) threw: " + KBMDoom.summarizeThrowable(throwable));
        }
        blitStatus = "all_ctors_failed";
        blitError = "no usable Texture constructor";
        KBMDoom.blitLog("createTextureReflective: giving up safely");
        return null;
    }

    private static boolean uploadGameplayTextureIfNeeded() {
        if (blitPermanentlyDisabled) {
            return false;
        }
        if (pixelBuffer == null || pixelBuffer.length == 0) {
            return false;
        }
        int n = KBMDoom.getWidth();
        int n2 = KBMDoom.getHeight();
        if (n <= 0 || n2 <= 0) {
            return false;
        }
        long l = KBMDoom.getFrameCount();
        if (l >= 0L && l == gameplayTextureFrame && gameplayTexture != null) {
            return true;
        }
        if (gameplayTexture == null || gameplayTextureW != n || gameplayTextureH != n2) {
            KBMDoom.blitLog("upload: (re)creating texture " + n + "x" + n2);
            gameplayTexture = KBMDoom.createTextureReflective(n, n2);
            if (gameplayTexture == null) {
                gameplayTextureBuffer = null;
                gameplayTextureW = 0;
                gameplayTextureH = 0;
                gameplayTextureFrame = -1L;
                blitPermanentlyDisabled = true;
                KBMDoom.blitLog("upload: createTexture returned null; disabling blit permanently (falling back to RLE)");
                return false;
            }
            gameplayTextureW = n;
            gameplayTextureH = n2;
            gameplayTextureBuffer = ByteBuffer.allocateDirect(n * n2 * 4).order(ByteOrder.nativeOrder());
            gameplayTextureFrame = -1L;
            KBMDoom.blitLog("upload: texture object created; buffer allocated " + n * n2 * 4 + " bytes");
        }
        if (gameplayTextureBuffer == null) {
            return false;
        }
        gameplayTextureBuffer.clear();
        int n3 = Math.min(pixelBuffer.length, n * n2);
        for (int i = 0; i < n3; ++i) {
            int n4 = pixelBuffer[i];
            gameplayTextureBuffer.put((byte)(n4 >> 16 & 0xFF));
            gameplayTextureBuffer.put((byte)(n4 >> 8 & 0xFF));
            gameplayTextureBuffer.put((byte)(n4 & 0xFF));
            gameplayTextureBuffer.put((byte)-1);
        }
        gameplayTextureBuffer.flip();
        try {
            if (gameplayTextureFrame < 0L) {
                KBMDoom.blitLog("upload: first-frame setData, bufferLimit=" + gameplayTextureBuffer.limit() + " class=" + gameplayTexture.getClass().getName());
            }
            Method method = gameplayTexture.getClass().getMethod("setData", ByteBuffer.class);
            method.invoke(gameplayTexture, gameplayTextureBuffer);
            if (gameplayTextureFrame < 0L) {
                KBMDoom.blitLog("upload: first setData returned OK");
            }
            gameplayTextureFrame = l;
            blitStatus = "upload_ok";
            return true;
        }
        catch (Throwable throwable) {
            gameplayTextureFrame = -1L;
            blitStatus = "upload_failed";
            blitError = KBMDoom.summarizeThrowable(throwable);
            KBMDoom.blitLog("upload: setData threw: " + blitError);
            return false;
        }
    }

    public static int getPixel(int n) {
        if (pixelBuffer == null || n < 0 || n >= pixelBuffer.length) {
            return 0;
        }
        return pixelBuffer[n] & 0xFFFFFF;
    }

    public static int getWidth() {
        if (engine != null && engine.isInitialized()) {
            return engine.getWidth();
        }
        return 320;
    }

    public static int getHeight() {
        if (engine != null && engine.isInitialized()) {
            return engine.getHeight();
        }
        return 200;
    }

    public static void setQuantizationBits(int n) {
        if (n < 0) {
            n = 0;
        }
        if (n > 4) {
            n = 4;
        }
        quantizationBits = n;
    }

    public static int getQuantizationBits() {
        return quantizationBits;
    }

    public static void setDisplayHz(int n) {
        if (n < 0) {
            n = 0;
        }
        displayHz = n;
        if (engine != null && engine.isInitialized()) {
            engine.setDisplayHz(n);
        }
    }

    public static int getDisplayHz() {
        return displayHz;
    }

    public static void setInstantStop(boolean bl) {
        if (engine != null) {
            engine.setInstantStop(bl);
        }
    }

    public static boolean getInstantStop() {
        return engine != null && engine.getInstantStop();
    }

    public static void setMouseTurnDelta(int n) {
        if (engine != null && engine.isInitialized()) {
            engine.setMouseTurnDelta(n);
        }
    }

    public static String getBlitStatus() {
        return blitStatus;
    }

    public static String getBlitError() {
        return blitError;
    }

    public static String getHudText() {
        if (engine != null && engine.isInitialized()) {
            return engine.getHudText();
        }
        return "";
    }

    public static String getMessageText() {
        if (engine != null && engine.isInitialized()) {
            return engine.getMessageText();
        }
        return "";
    }

    public static long getFrameCount() {
        if (engine != null && engine.isInitialized()) {
            return engine.getFrameCount();
        }
        return -1L;
    }

    public static boolean supportsGameplayBlit() {
        if (!gameplayBlitRequested) {
            return false;
        }
        return pixelBuffer != null && KBMDoom.getWidth() > 0 && KBMDoom.getHeight() > 0;
    }

    public static void setGameplayBlitEnabled(boolean bl) {
        gameplayBlitRequested = bl;
        if (!bl) {
            gameplayTexture = null;
            gameplayTextureBuffer = null;
            gameplayTextureW = 0;
            gameplayTextureH = 0;
            gameplayTexturePotW = 0;
            gameplayTexturePotH = 0;
            gameplayTextureFrame = -1L;
        }
    }

    public static String getGameplayBlitSurfaceKey() {
        return KBMDoom.supportsGameplayBlit() ? "java-texture" : "";
    }

    public static Object getGameplayTexture() {
        if (!KBMDoom.supportsGameplayBlit()) {
            return null;
        }
        return KBMFrameTexture.update((String)"doom", (int[])pixelBuffer, (int)KBMDoom.getWidth(), (int)KBMDoom.getHeight());
    }

    public static String getFrameTextureStatus() {
        return KBMFrameTexture.getStatus((String)"doom");
    }

    public static String getFrameTextureInfo() {
        return KBMFrameTexture.getInfo((String)"doom");
    }

    private static int quantize24(int n) {
        int n2 = quantizationBits;
        if (n2 <= 0) {
            return n & 0xFFFFFF;
        }
        int n3 = 255 << n2 & 0xFF;
        int n4 = n >> 16 & 0xFF;
        int n5 = n >> 8 & 0xFF;
        int n6 = n & 0xFF;
        return (n4 &= n3) << 16 | (n5 &= n3) << 8 | (n6 &= n3);
    }

    private static void appendRect(StringBuilder stringBuilder, int n, int n2, int n3, int n4, int n5) {
        stringBuilder.append(n).append(',').append(n2).append(',').append(n3).append(',').append(n4).append(',').append(n5).append(',');
    }

    public static String getRegionRLE(int n, int n2, int n3, int n4, int n5, int n6, int n7, int n8, int n9) {
        if (pixelBuffer == null) {
            return "";
        }
        int n10 = KBMDoom.getWidth();
        int n11 = KBMDoom.getHeight();
        if (n2 <= n || n4 <= n3 || n7 <= 0 || n8 <= 0 || n9 < 1) {
            return "";
        }
        int n12 = n7 / n9;
        int n13 = n8 / n9;
        if (n12 < 1) {
            n12 = 1;
        }
        if (n13 < 1) {
            n13 = 1;
        }
        int n14 = n2 - n;
        int n15 = n4 - n3;
        StringBuilder stringBuilder = new StringBuilder(n12 * n13 * 8);
        HashMap<String, int[]> hashMap = new HashMap<String, int[]>();
        for (int i = 0; i < n13; ++i) {
            int n16 = n3 + i * n15 / n13;
            int n17 = n3 + (i + 1) * n15 / n13;
            if (n16 < 0) {
                n16 = 0;
            }
            if (n17 > n11) {
                n17 = n11;
            }
            if (n17 <= n16) {
                n17 = n16 + 1;
            }
            if (n16 >= n11) {
                n16 = n11 - 1;
            }
            if (n17 > n11) {
                n17 = n11;
            }
            int n18 = n6 + i * n9;
            int n19 = 1;
            int n20 = n9 + n19;
            HashSet<String> hashSet = new HashSet<String>();
            int n21 = 0;
            int n22 = -1;
            for (int j = 0; j <= n12; ++j) {
                int n23;
                int n24;
                int n25 = -1;
                if (j < n12) {
                    int n26;
                    n24 = n + j * n14 / n12;
                    n23 = n + (j + 1) * n14 / n12;
                    if (n24 < 0) {
                        n24 = 0;
                    }
                    if (n23 > n10) {
                        n23 = n10;
                    }
                    if (n23 <= n24) {
                        n23 = n24 + 1;
                    }
                    int n27 = n24;
                    int n28 = n23;
                    int n29 = n16;
                    int n30 = n17;
                    long l = 0L;
                    long l2 = 0L;
                    long l3 = 0L;
                    int n31 = 0;
                    for (n26 = n29; n26 < n30; ++n26) {
                        int n32 = n26 * n10;
                        for (int k = n27; k < n28; ++k) {
                            int n33 = n32 + k;
                            if (n33 < 0 || n33 >= pixelBuffer.length) continue;
                            int n34 = pixelBuffer[n33] & 0xFFFFFF;
                            l += (long)(n34 >> 16 & 0xFF);
                            l2 += (long)(n34 >> 8 & 0xFF);
                            l3 += (long)(n34 & 0xFF);
                            ++n31;
                        }
                    }
                    if (n31 == 0) {
                        n31 = 1;
                    }
                    n26 = (int)(l / (long)n31) << 16 | (int)(l2 / (long)n31) << 8 | (int)(l3 / (long)n31);
                    n25 = KBMDoom.quantize24(n26);
                }
                if (n25 == n22) continue;
                if (n22 >= 0) {
                    n24 = n5 + n21 * n9;
                    n23 = (j - n21) * n9 + n19;
                    String string = n24 + "|" + n23 + "|" + n22;
                    int[] nArray = (int[])hashMap.get(string);
                    if (nArray != null && nArray[4] == i - 1) {
                        nArray[3] = nArray[3] + n9;
                        nArray[4] = i;
                    } else {
                        if (nArray != null) {
                            KBMDoom.appendRect(stringBuilder, nArray[0], nArray[1], nArray[2], nArray[3], nArray[5]);
                        }
                        nArray = new int[]{n24, n18, n23, n20, i, n22};
                        hashMap.put(string, nArray);
                    }
                    hashSet.add(string);
                }
                n21 = j;
                n22 = n25;
            }
            Iterator<Map.Entry<String, int[]>> iterator = hashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, int[]> entry = iterator.next();
                if (hashSet.contains(entry.getKey())) continue;
                int[] nArray = (int[])entry.getValue();
                KBMDoom.appendRect(stringBuilder, nArray[0], nArray[1], nArray[2], nArray[3], nArray[5]);
                iterator.remove();
            }
        }
        for (int[] nArray : hashMap.values()) {
            KBMDoom.appendRect(stringBuilder, nArray[0], nArray[1], nArray[2], nArray[3], nArray[5]);
        }
        return stringBuilder.toString();
    }

    public static String getRowRLE(int n) {
        int n2;
        if (pixelBuffer == null) {
            return "";
        }
        int n3 = KBMDoom.getWidth();
        int n4 = KBMDoom.getHeight();
        if (n < 0 || n >= n4) {
            return "";
        }
        int n5 = n * n3;
        int n6 = n5 + n3;
        if (n6 > pixelBuffer.length) {
            n6 = pixelBuffer.length;
        }
        if ((n2 = n6 - n5) <= 0) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder(n2 * 6);
        int n7 = 0;
        int n8 = pixelBuffer[n5] & 0xFFFFFF;
        for (int i = 1; i <= n2; ++i) {
            int n9;
            int n10 = n9 = i < n2 ? pixelBuffer[n5 + i] & 0xFFFFFF : -1;
            if (n9 == n8) continue;
            stringBuilder.append(n7).append(',').append(i - n7).append(',').append(n8).append(';');
            n7 = i;
            n8 = n9;
        }
        return stringBuilder.toString();
    }

    public static boolean isReady() {
        return engine != null && engine.isInitialized();
    }

    public static String getGameState() {
        if (engine == null) {
            return "UNKNOWN";
        }
        return engine.getGameState();
    }

    private static void setTestProbeStatus(String string, Throwable throwable) {
        String string2 = testTextureProbeStatus = string == null ? "unknown" : string;
        if (throwable != null) {
            testTextureProbeError = KBMDoom.summarizeThrowable(throwable);
        }
    }

    public static String getTestTextureProbeStatus() {
        return testTextureProbeStatus;
    }

    public static String getTestTextureProbeError() {
        return testTextureProbeError;
    }

    public static String getBridgeVersion() {
        return BRIDGE_VERSION;
    }

    public static String getCapabilities() {
        return BRIDGE_CAPABILITIES;
    }

    public static String runTextureProbe(String string) {
        try {
            String string2;
            String string3 = string2 = string == null ? "" : string.trim().toLowerCase();
            if (string2.length() == 0 || "status".equals(string2)) {
                return testTextureProbeStatus;
            }
            if ("reset".equals(string2)) {
                KBMDoom.destroyTestTexture();
                return testTextureProbeStatus;
            }
            if ("create".equals(string2)) {
                return KBMDoom.createTestTextureObjectOnly() ? "ok_create" : testTextureProbeStatus;
            }
            if ("upload".equals(string2)) {
                return KBMDoom.uploadTestTextureSolid() ? "ok_upload" : testTextureProbeStatus;
            }
            if ("create_upload".equals(string2) || "create_upload_draw".equals(string2)) {
                if (!KBMDoom.createTestTextureObjectOnly()) {
                    return testTextureProbeStatus;
                }
                if (!KBMDoom.uploadTestTextureSolid()) {
                    return testTextureProbeStatus;
                }
                return "ok_upload";
            }
            KBMDoom.setTestProbeStatus("bad_probe_mode", null);
            return testTextureProbeStatus;
        }
        catch (Throwable throwable) {
            KBMDoom.setTestProbeStatus("fail_probe_exception", throwable);
            return testTextureProbeStatus;
        }
    }

    public static boolean createTestTextureObjectOnly() {
        try {
            KBMDoom.setTestProbeStatus("begin_create", null);
            testTextureProbeError = "";
            testTexture = KBMDoom.createTextureReflective(32, 32);
            if (testTexture == null) {
                KBMDoom.setTestProbeStatus("fail_create_texture_null", null);
                return false;
            }
            KBMDoom.setTestProbeStatus("ok_create", null);
            return true;
        }
        catch (Throwable throwable) {
            KBMDoom.setTestProbeStatus("fail_create_exception", throwable);
            testTexture = null;
            testTextureBuffer = null;
            return false;
        }
    }

    public static boolean uploadTestTextureSolid() {
        if (testTexture == null) {
            KBMDoom.setTestProbeStatus("fail_upload_no_texture", null);
            return false;
        }
        try {
            KBMDoom.setTestProbeStatus("begin_upload", null);
            testTextureBuffer = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder());
        }
        catch (Throwable throwable) {
            KBMDoom.setTestProbeStatus("fail_alloc_buffer", throwable);
            testTextureBuffer = null;
            return false;
        }
        try {
            testTextureBuffer.clear();
            for (int i = 0; i < 32; ++i) {
                for (int j = 0; j < 32; ++j) {
                    byte by;
                    byte by2;
                    byte by3;
                    int n = j / 4;
                    int n2 = i / 4;
                    int n3 = (n + n2) % 2;
                    if (n3 == 0) {
                        by3 = -1;
                        by2 = -1;
                        by = -1;
                    } else {
                        by3 = 0;
                        by2 = 0;
                        by = -56;
                    }
                    testTextureBuffer.put(by3);
                    testTextureBuffer.put(by2);
                    testTextureBuffer.put(by);
                    testTextureBuffer.put((byte)-1);
                }
            }
            testTextureBuffer.flip();
            KBMDoom.setTestProbeStatus("ok_fill", null);
        }
        catch (Throwable throwable) {
            KBMDoom.setTestProbeStatus("fail_fill_buffer", throwable);
            return false;
        }
        try {
            testTexture.getClass().getMethod("setData", ByteBuffer.class).invoke(testTexture, testTextureBuffer);
            KBMDoom.setTestProbeStatus("ok_upload", null);
            return true;
        }
        catch (Throwable throwable) {
            KBMDoom.setTestProbeStatus("fail_upload_setdata", throwable);
            return false;
        }
    }

    public static boolean createTestTexture() {
        if (!KBMDoom.createTestTextureObjectOnly()) {
            return false;
        }
        return KBMDoom.uploadTestTextureSolid();
    }

    public static Object getTestTexture() {
        return testTexture;
    }

    public static void destroyTestTexture() {
        testTexture = null;
        testTextureBuffer = null;
        KBMDoom.setTestProbeStatus("destroyed", null);
        testTextureProbeError = "";
    }

    public static void destroy() {
        if (engine != null) {
            engine.destroy();
            engine = null;
        }
        tickFatal = false;
        tickFatalReason = "";
        pixelBuffer = new int[64000];
        gameplayBlitRequested = false;
        gameplayTexture = null;
        gameplayTextureBuffer = null;
        gameplayTextureW = 0;
        gameplayTextureH = 0;
        gameplayTextureFrame = -1L;
        testTexture = null;
        testTextureBuffer = null;
        testTextureProbeStatus = "idle";
        testTextureProbeError = "";
    }

    static {
        pixelBuffer = new int[64000];
        quantizationBits = 2;
        displayHz = 0;
        gameplayBlitRequested = false;
        gameplayTextureW = 0;
        gameplayTextureH = 0;
        gameplayTextureFrame = -1L;
        testTextureProbeStatus = "idle";
        testTextureProbeError = "";
        blitStatus = "idle";
        blitError = "";
        blitPermanentlyDisabled = false;
        gameplayTexturePotW = 0;
        gameplayTexturePotH = 0;
    }
}
