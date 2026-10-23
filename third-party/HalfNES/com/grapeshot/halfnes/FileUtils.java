/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtils {
    private FileUtils() {
    }

    public static String getExtension(File file) {
        return FileUtils.getExtension(file.getName());
    }

    public static String getExtension(String string) {
        if (string == null || string.equals("")) {
            return "";
        }
        int n = string.lastIndexOf(46);
        if (n < 0) {
            return "";
        }
        return string.substring(n);
    }

    public static String stripExtension(File file) {
        String string = file.getName();
        if (string == null || string.equals("")) {
            return "";
        }
        int n = string.lastIndexOf(46);
        if (n < 0) {
            return "";
        }
        return string.substring(0, n);
    }

    public static String stripExtension(String string) {
        if (string == null || string.equals("")) {
            return "";
        }
        int n = string.lastIndexOf(46);
        if (n < 0) {
            return "";
        }
        return string.substring(0, n);
    }

    public static void writetofile(int[] nArray, String string) {
        AsyncWriter asyncWriter = new AsyncWriter(nArray, string);
        asyncWriter.run();
    }

    public static void asyncwritetofile(int[] nArray, String string) {
        AsyncWriter asyncWriter = new AsyncWriter(nArray, string);
        EventQueue.invokeLater(asyncWriter);
    }

    public static String getFilenamefromPath(String string) {
        return new File(string).getName();
    }

    public static int[] readfromfile(String string) {
        File file = new File(string);
        byte[] byArray = new byte[(int)file.length()];
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            fileInputStream.read(byArray);
        }
        catch (IOException iOException) {
            System.err.println("Failed to load file");
            iOException.printStackTrace();
        }
        int[] nArray = new int[byArray.length];
        for (int i = 0; i < byArray.length; ++i) {
            nArray[i] = (short)(byArray[i] & 0xFF);
        }
        return nArray;
    }

    public static boolean exists(String string) {
        File file = new File(string);
        return file.canRead() && !file.isDirectory();
    }

    private static class AsyncWriter
    implements Runnable {
        private final int[] a;
        private final String path;

        public AsyncWriter(int[] nArray, String string) {
            this.a = nArray;
            this.path = string;
        }

        @Override
        public void run() {
            if (this.a != null && this.path != null) {
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(this.path);
                    byte[] byArray = new byte[this.a.length];
                    for (int i = 0; i < this.a.length; ++i) {
                        byArray[i] = (byte)(this.a[i] & 0xFF);
                    }
                    fileOutputStream.write(byArray);
                    fileOutputStream.flush();
                    fileOutputStream.close();
                }
                catch (IOException iOException) {
                    System.err.print("Could not save. ");
                    System.err.println(iOException);
                }
            }
        }
    }
}

