package com.github.tvbox.osc.util;

import android.text.TextUtils;

public class StringUtils {
    public static boolean isEmpty(String text) {
        return TextUtils.isEmpty(text);
    }

    public static boolean isNotEmpty(String text) {
        return !TextUtils.isEmpty(text);
    }
}
