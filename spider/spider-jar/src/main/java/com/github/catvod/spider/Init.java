package com.github.catvod.spider;

import android.app.Application;
import android.content.Context;
import dalvik.system.DexClassLoader;

public class Init {
    private static Application app;
    private static DexClassLoader loader;

    public static void init(Context context) {
        app = (Application) context.getApplicationContext();
    }

    public static Application context() {
        return app;
    }

    public static DexClassLoader loader() {
        return loader;
    }

    public static void setLoader(DexClassLoader cl) {
        loader = cl;
    }

    public static ClassLoader classLoader() {
        return loader != null ? loader : (app != null ? app.getClassLoader() : null);
    }

    public static com.github.catvod.crawler.Spider getSpider(String className) {
        if (loader == null) return null;
        try {
            return (com.github.catvod.crawler.Spider) loader.loadClass("com.github.catvod.spider." + className).newInstance();
        } catch (Throwable t) {
            return null;
        }
    }
}
