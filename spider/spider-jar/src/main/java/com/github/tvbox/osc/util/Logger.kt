package com.github.tvbox.osc.util

import android.util.Log

object Logger {
    private const val TAG = "NewBox-JS"

    @JvmStatic
    @JvmOverloads
    fun d(tag: String = TAG, msg: String, tr: Throwable? = null) {
        Log.d(tag, msg, tr)
    }

    @JvmStatic
    @JvmOverloads
    fun i(tag: String = TAG, msg: String, tr: Throwable? = null) {
        Log.i(tag, msg, tr)
    }

    @JvmStatic
    @JvmOverloads
    fun w(tag: String = TAG, msg: String, tr: Throwable? = null) {
        Log.w(tag, msg, tr)
    }

    @JvmStatic
    @JvmOverloads
    fun e(tag: String = TAG, msg: String, tr: Throwable? = null) {
        Log.e(tag, msg, tr)
    }
}
