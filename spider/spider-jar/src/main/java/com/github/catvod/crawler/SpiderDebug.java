package com.github.catvod.crawler;

import com.github.tvbox.osc.util.Logger;

public class SpiderDebug {
    public static void log(Throwable th) {
        try {
            Logger.d("SpiderLog", th.getMessage(), th);
        } catch (Throwable th1) {
        }
    }

    public static void log(String msg) {
        try {
            Logger.d("SpiderLog", msg);
        } catch (Throwable th1) {
        }
    }

    public static String ec(int i) {
        return "";
    }
}
