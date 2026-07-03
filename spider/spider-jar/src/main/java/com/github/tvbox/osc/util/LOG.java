package com.github.tvbox.osc.util;

import android.util.Log;

public class LOG {
    private static final String TAG = "NewBox-JS";

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    public static void e(String tag, Throwable tr) {
        Log.e(tag, tr.getMessage(), tr);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    public static void e(Throwable tr) {
        Log.e(TAG, tr.getMessage(), tr);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
    }
}
