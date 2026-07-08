package com.github.tvbox.osc.util;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.Init;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FileUtils {
    private static final Pattern URLJOIN = Pattern.compile("^http.*\\.(js|txt|json|m3u)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final OkHttpClient CLIENT = new OkHttpClient();

    public static File open(String str) {
        Context context = Init.context();
        File dir = context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir();
        return new File(dir, "qjscache_" + str + ".js");
    }

    public static String genUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    public static String getCache(String name) {
        try {
            File file = open(name);
            String code = file.exists() ? new String(readSimple(file)) : "";
            if (TextUtils.isEmpty(code)) return "";
            JsonObject object = new Gson().fromJson(code, JsonObject.class).getAsJsonObject();
            if (object.get("expires").getAsInt() > System.currentTimeMillis() / 1000) {
                return new String(Base64.decode(object.get("data").getAsString(), Base64.URL_SAFE));
            }
            recursiveDelete(open(name));
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    public static byte[] getCacheByte(String name) {
        try {
            File file = open("B_" + name);
            return file.exists() ? readSimple(file) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void setCache(int time, String name, String data) {
        try {
            JSONObject object = new JSONObject();
            object.put("expires", (int) (time + (System.currentTimeMillis() / 1000)));
            object.put("data", Base64.encodeToString(data.getBytes(), Base64.URL_SAFE));
            writeSimple(object.toString().getBytes(), open(name));
        } catch (Exception e) {
            Logger.e("NewBox-JS", e.toString(), e);
        }
    }

    public static void setCacheByte(String name, byte[] data) {
        try {
            writeSimple(byteMerger("//DRPY".getBytes(), Base64.encode(data, Base64.URL_SAFE)), open("B_" + name));
        } catch (Exception e) {
            Logger.e("NewBox-JS", e.toString(), e);
        }
    }

    public static byte[] byteMerger(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    public static String get(String url) {
        return get(url, null);
    }

    public static String get(String url, Map<String, String> headers) {
        try {
            Request.Builder builder = new Request.Builder().url(url);
            if (headers != null) builder.headers(Headers.of(headers));
            else builder.header("User-Agent", url.startsWith("https://gitcode.net/") ? "okhttp/3.15" : "okhttp/3.15");
            try (Response response = CLIENT.newCall(builder.build()).execute()) {
                if (response.isSuccessful() && response.body() != null) return new String(response.body().bytes(), "UTF-8");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String loadModule(String name) {
        try {
            if (name.endsWith("ali.js")) name = "ali.js";
            else if (name.endsWith("ali_api.js")) name = "ali_api.js";
            else if (name.contains("similarity.js")) name = "similarity.js";
            else if (name.contains("gbk.js")) name = "gbk.js";
            else if (name.contains("模板.js")) name = "模板.js";
            else if (name.contains("cat.js")) name = "cat.js";

            Matcher matcher = URLJOIN.matcher(name);
            if (matcher.find()) {
                String cache = getCache(MD5.encode(name));
                if (StringUtils.isEmpty(cache)) {
                    String netStr = get(name);
                    if (!TextUtils.isEmpty(netStr)) setCache(604800, MD5.encode(name), netStr);
                    return netStr;
                }
                return cache;
            } else if (name.startsWith("assets://")) {
                return getAsOpen(name.substring(9));
            } else if (isAsFile(name, "js/lib")) {
                return getAsOpen("js/lib/" + name);
            } else if (name.startsWith("file://")) {
                return get(name.replace("file://", ""));
            }
        } catch (Exception e) {
            Logger.e("NewBox-JS", e.toString(), e);
        }
        return name;
    }

    public static boolean isAsFile(String name, String path) {
        try {
            String[] files = Init.context().getAssets().list(path);
            if (files == null) return false;
            for (String file : files) if (file.equals(name.trim())) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String getAsOpen(String name) {
        try (InputStream input = Init.context().getAssets().open(name)) {
            byte[] data = new byte[input.available()];
            input.read(data);
            return new String(data, "UTF-8");
        } catch (Exception e) {
            Logger.e("NewBox-JS", e.toString(), e);
        }
        return "";
    }

    public static boolean writeSimple(byte[] data, File dst) {
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(dst))) {
            output.write(data);
            return true;
        } catch (Exception e) {
            Logger.e("NewBox-JS", e.toString(), e);
            return false;
        }
    }

    public static byte[] readSimple(File src) {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(src))) {
            byte[] data = new byte[input.available()];
            input.read(data);
            return data;
        } catch (Exception e) {
            Logger.e("NewBox-JS", e.toString(), e);
            return new byte[0];
        }
    }

    public static boolean recursiveDelete(File file) {
        if (file == null || !file.exists()) return false;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) recursiveDelete(child);
        }
        return file.delete();
    }
}
