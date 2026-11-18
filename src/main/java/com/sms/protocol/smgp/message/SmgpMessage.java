package com.sms.protocol.smgp.message;


import com.sms.protocol.smgp.SmgpConstants;
import io.netty.buffer.ByteBuf;
import lombok.Data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
@Data
public abstract class SmgpMessage {

    private SmgpHeader header;

    /** 10 字节 MsgID 的两种常见编码格式 */
    public static class MsgIdData {
        /** 统一文本表示：ASCII 字符串原样；long 格式转为十进制字符串 */
        private final String text;
        /** 二进制 long 格式（2 字节 0x00 + 8 字节 long）时非空 */
        private final Long asLong;

        public MsgIdData(String text, Long asLong) {
            this.text = text;
            this.asLong = asLong;
        }
        public String getText()   { return text; }
        public Long getAsLong()   { return asLong; }
    }

    /**
     * 读取 10 字节 MsgID，兼容两种格式：
     * <ol>
     *   <li>标准 Octet String（ASCII 定长，0x00 结尾截断）——常见网关实现</li>
     *   <li>部分网关/挡板实现：2 字节 0x00 填充 + 8 字节 long（网络序大端）</li>
     * </ol>
     */
    protected MsgIdData readMsgId(ByteBuf buf) {
        byte[] raw = new byte[10];
        buf.readBytes(raw);

        if (raw[0] == 0x00 && raw[1] == 0x00) {
            long value = ByteBuffer.wrap(raw, 2, 8).getLong();
            return new MsgIdData(String.valueOf(value), value);
        }
        int i = 0;
        while (i < raw.length && raw[i] != 0) i++;
        return new MsgIdData(new String(raw, 0, i, StandardCharsets.US_ASCII), null);
    }

    /**
     * 写 10 字节 MsgID：asLong 非空时按「2 字节 0x00 + 8 字节 long」写出，
     * 否则按 ASCII 定长字符串写出（与读取格式对称）。
     */
    protected void writeMsgId(ByteBuf buf, String text, Long asLong) {
        if (asLong != null) {
            buf.writeByte(0);
            buf.writeByte(0);
            buf.writeLong(asLong);
        } else {
            writeFixedString(buf, text, 10);
        }
    }

    public SmgpMessage() {
        initHeader();
    }

    // 用于获取当前Body的字节长度，子类必须实现
    public abstract int getBodyLength();

    public abstract void encodeBody(ByteBuf buf);
    public abstract void decodeBody(ByteBuf buf);

    public abstract void initHeader();

    // 计算总长度并设置到Header中
    public void calcAndSetPacketLength() {
        this.header.setPacketLength(SmgpConstants.HEADER_LENGTH + getBodyLength());
    }

    public SmgpHeader getHeader() {
        return header;
    }

    /**
     * 获取命令字（便捷方法，与 CMPP/SGIP 的 Message 保持一致）
     */
    public int getCommandId() {
        return header == null ? 0 : header.getCommandId();
    }

    /**
     * 写定长字符串：左对齐，不足补 0x00，超出截断。
     * SMGP 的 Octet String 字段（号码、业务代码、时间等）均为 ASCII 字符，
     * 中文短信内容不在此列（内容使用 MsgFormat 对应编码写入 msgContent）。
     */
    protected void writeFixedString(ByteBuf buf, String value, int length) {
        if (value == null) {
            // 如果字符串为空，填充空白（这里用'\0'填充）
            for (int i = 0; i < length; i++) {
                buf.writeByte(0);
            }
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            int strLen = bytes.length;

            if (strLen > length) {
                // 如果字符串长度超过预定长度，则截断
                buf.writeBytes(bytes, 0, length);
            } else {
                // 如果字符串长度不足预定长度，则填充空白
                buf.writeBytes(bytes);
                for (int i = strLen; i < length; i++) {
                    buf.writeByte(0); // 填充 '\0'
                }
            }
        }
    }

    protected String readFixedString(ByteBuf buf, int length) {
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        int i = 0;
        while (i < bytes.length && bytes[i] != 0) i++;
        return new String(bytes, 0, i, StandardCharsets.US_ASCII);
    }




}
