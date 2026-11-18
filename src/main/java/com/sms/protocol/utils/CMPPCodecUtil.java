package com.sms.protocol.utils;

import io.netty.buffer.ByteBuf;
import java.nio.charset.Charset;

public final class CMPPCodecUtil {

    public static final Charset GBK = Charset.forName("GBK");

    private CMPPCodecUtil() {}

    /** 写入定长 C-Octet String，不足补 0，超长截断 */
    public static void writeCString(ByteBuf out, String str, int length) {
        byte[] bytes = str == null ? new byte[0] : str.getBytes(GBK);
        int writeLen = Math.min(bytes.length, length);
        out.writeBytes(bytes, 0, writeLen);
        for (int i = writeLen; i < length; i++) {
            out.writeByte(0);
        }
    }

    /** 读取定长 C-Octet String，遇到 0x00 截断 */
    public static String readCString(ByteBuf in, int length) {
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return readCString(bytes);
    }

    public static String readCString(byte[] bytes) {
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end, GBK);
    }
}
