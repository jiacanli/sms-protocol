package com.sms.protocol.utils;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;

/**
 * SGIP 协议编解码工具类
 * 与 CMPPCodecUtil 风格保持一致，统一处理定长字符串、序列号等通用编解码逻辑
 */
public class SGIPCodecUtil {

    /**
     * 默认字符集：GBK，符合国内电信协议惯例
     * 如需 ASCII/UTF-16BE 可通过重载方法手动指定
     */
    private static final Charset DEFAULT_CHARSET = Charset.forName("GBK");

    private SGIPCodecUtil() {
        // 工具类禁止实例化
    }

    // ==================== 定长字符串读写 ====================

    /**
     * 写入定长字符串（默认GBK编码）
     * 不足指定长度时尾部补 0x00，超出时截断前N字节
     *
     * @param out    输出缓冲区
     * @param str    待写入字符串
     * @param length 固定字节长度
     */
    public static void writeCString(ByteBuf out, String str, int length) {
        writeCString(out, str, length, DEFAULT_CHARSET);
    }

    /**
     * 写入定长字符串（指定字符集）
     *
     * @param out     输出缓冲区
     * @param str     待写入字符串
     * @param length  固定字节长度
     * @param charset 字符集
     */
    public static void writeCString(ByteBuf out, String str, int length, Charset charset) {
        if (str == null) {
            str = "";
        }
        byte[] bytes = str.getBytes(charset);

        if (bytes.length >= length) {
            // 超出长度，截断前length字节
            out.writeBytes(bytes, 0, length);
        } else {
            // 写入内容 + 尾部补0
            out.writeBytes(bytes);
            for (int i = bytes.length; i < length; i++) {
                out.writeByte(0x00);
            }
        }
    }

    /**
     * 读取定长字符串（默认GBK编码）
     * 自动去除尾部 0x00 填充
     *
     * @param in     输入缓冲区
     * @param length 固定字节长度
     * @return 解析后的字符串
     */
    public static String readCString(ByteBuf in, int length) {
        return readCString(in, length, DEFAULT_CHARSET);
    }

    /**
     * 读取定长字符串（指定字符集）
     *
     * @param in     输入缓冲区
     * @param length 固定字节长度
     * @param charset 字符集
     * @return 解析后的字符串
     */
    public static String readCString(ByteBuf in, int length, Charset charset) {
        byte[] bytes = new byte[length];
        in.readBytes(bytes);

        // 从后往前找到第一个非0字节，去除尾部填充
        int endIndex = length;
        for (int i = length - 1; i >= 0; i--) {
            if (bytes[i] != 0x00) {
                endIndex = i + 1;
                break;
            }
        }

        return new String(bytes, 0, endIndex, charset);
    }

    // ==================== SGIP 专属：12字节序列号读写 ====================

    /**
     * 写入 SGIP 12字节序列号
     * 结构：源节点号(4字节大端) + 目的节点号(4字节大端) + 流水号(4字节大端)
     *
     * @param out            输出缓冲区
     * @param sourceNodeId   源节点号（无符号4字节）
     * @param destNodeId     目的节点号（无符号4字节）
     * @param sequenceNumber 流水号（无符号4字节）
     */
    public static void writeSequenceId(ByteBuf out, long sourceNodeId, long destNodeId, long sequenceNumber) {
        out.writeInt((int) sourceNodeId);
        out.writeInt((int) destNodeId);
        out.writeInt((int) sequenceNumber);
    }

    /**
     * 读取 SGIP 12字节序列号
     *
     * @param in 输入缓冲区
     * @return long数组：[源节点号, 目的节点号, 流水号]，统一用long避免无符号溢出
     */
    public static long[] readSequenceId(ByteBuf in) {
        long sourceNodeId = in.readUnsignedInt();
        long destNodeId = in.readUnsignedInt();
        long sequenceNumber = in.readUnsignedInt();
        return new long[]{sourceNodeId, destNodeId, sequenceNumber};
    }

    // ==================== 辅助工具 ====================

    /**
     * 字节数组转十六进制字符串，用于调试打印报文内容
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
