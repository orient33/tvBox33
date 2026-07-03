package com.github.tvbox.osc.util.js;

import androidx.annotation.Keep;

import com.whl.quickjs.wrapper.Function;

import java.util.concurrent.ConcurrentHashMap;

public class local {
    private static final ConcurrentHashMap<String, String> DATA = new ConcurrentHashMap<>();

    @Keep
    @Function
    public void delete(String str, String str2) {
        DATA.remove(key(str, str2));
    }

    @Keep
    @Function
    public String get(String str, String str2) {
        return DATA.getOrDefault(key(str, str2), "");
    }

    @Keep
    @Function
    public void set(String str, String str2, String str3) {
        DATA.put(key(str, str2), str3);
    }

    private static String key(String str, String str2) {
        return "jsRuntime_" + str + "_" + str2;
    }
}
