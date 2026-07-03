package com.github.tvbox.osc.util;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

public class MD5 {
    private static final char[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String encode(String input) {
        return encode(input.getBytes());
    }

    public static String string2MD5(String input) {
        return input == null || input.isEmpty() ? "" : encode(input);
    }

    public static String getFileMd5(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[4096];
            int len;
            while ((len = input.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static String encode(byte[] input) {
        try {
            return toHex(MessageDigest.getInstance("MD5").digest(input));
        } catch (Exception e) {
            return "";
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        int i = 0;
        for (byte b : bytes) {
            out[i++] = HEX[(b >>> 4) & 0x0f];
            out[i++] = HEX[b & 0x0f];
        }
        return new String(out);
    }
}
