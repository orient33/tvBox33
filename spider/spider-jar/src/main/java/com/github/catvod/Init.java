package com.github.catvod;

import android.content.Context;
import java.lang.ref.WeakReference;

public class Init {
    private WeakReference<Context> context;
    private Context strongRef;

    private static class Loader {
        static volatile Init INSTANCE = new Init();
    }

    private static Init get() {
        return Loader.INSTANCE;
    }

    public static void set(Context context) {
        get().strongRef = context.getApplicationContext();
        get().context = new WeakReference<>(context.getApplicationContext());
    }

    public static Context context() {
        Context ctx = get().context.get();
        if (ctx == null) ctx = get().strongRef;
        return ctx;
    }
}
