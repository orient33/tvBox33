package com.github.tvbox.newbox.spider.api

/** Thrown when a spider cannot be loaded (JAR download failure, class not found, init crash, etc.) */
class SpiderLoadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
