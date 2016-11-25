package com.haishinkit.util;

import java.nio.ByteBuffer;

public final class ByteBufferUtils {
    private ByteBufferUtils() {
    }

    public static String toHexString(ByteBuffer buffer) {
        StringBuilder builder = new StringBuilder();
        byte[] bytes = buffer.array();
        for (byte b : bytes) {
            builder.append(String.format("0x%02x,", b & 0xff));
        }
        return builder.toString();
    }
}
