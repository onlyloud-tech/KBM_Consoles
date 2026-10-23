/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes;

import com.grapeshot.halfnes.NES;
import java.util.prefs.Preferences;

public class PrefsSingleton {
    private static Preferences instance = null;

    protected PrefsSingleton() {
    }

    public static synchronized Preferences get() {
        if (instance == null) {
            instance = Preferences.userNodeForPackage(NES.class);
        }
        return instance;
    }
}

