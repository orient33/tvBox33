package com.github.tvbox.newbox.common

import android.util.Base64
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AES {

    fun rightPadding(key: String, replace: String, length: Int): String {
        val trimmed = key.trim()
        return when {
            trimmed.length > length -> trimmed.substring(0, length)
            trimmed.length == length -> trimmed
            else -> trimmed + replace.repeat(length - trimmed.length)
        }
    }

    fun toBytes(src: String): ByteArray {
        val len = src.length / 2
        val ret = ByteArray(len)
        for (i in 0 until len) {
            ret[i] = Integer.valueOf(src.substring(i * 2, i * 2 + 2), 16).toByte()
        }
        return ret
    }

    fun ecb(data: String, key: String): String? {
        return try {
            val paddedKey = rightPadding(key, "0", 16)
            val dataBytes = toBytes(data)
            val keySpec = SecretKeySpec(paddedKey.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            String(cipher.doFinal(dataBytes))
        } catch (e: Exception) {
            null
        }
    }

    fun cbc(data: String, key: String, iv: String): String? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val keySpec = SecretKeySpec(key.toByteArray(), "AES")
            val paramSpec: AlgorithmParameterSpec = IvParameterSpec(iv.toByteArray())
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec)
            String(cipher.doFinal(toBytes(data)))
        } catch (e: Exception) {
            null
        }
    }

    fun isJson(content: String): Boolean {
        return try {
            org.json.JSONObject(content)
            true
        } catch (_: Exception) {
            false
        }
    }
}
