package com.sms.protocol.smgp.message;

import io.netty.buffer.ByteBuf;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * SMGP 可选参数（TLV：Tag(2) + Length(2) + Value(N)）。
 * <p>
 * 定义见《中国电信短消息网关协议 V3.0.3》6.3 节，用于 Submit/Deliver/Forward 等消息体末尾。
 * 常见 Tag：TP_pid、TP_udhi、LinkID、PkTotal、PkNumber、SubmitMsgType、SPDealResult 等。
 */
@Data
public class SmgpTlv {

    /** Tag，2 字节无符号整数 */
    private int tag;
    /** Value 长度，2 字节无符号整数 */
    private int length;
    /** Value 内容 */
    private byte[] value;

    public SmgpTlv() {
    }

    public SmgpTlv(int tag, byte[] value) {
        this.tag = tag;
        this.value = value == null ? new byte[0] : value;
        this.length = this.value.length;
    }

    public SmgpTlv(int tag, int intValue) {
        this(tag, new byte[]{(byte) intValue});
    }

    /** 编码为 Tag(2) + Length(2) + Value(N) */
    public void encode(ByteBuf buf) {
        buf.writeShort(tag);
        buf.writeShort(value == null ? 0 : value.length);
        if (value != null && value.length > 0) {
            buf.writeBytes(value);
        }
    }

    /** 从缓冲区读取一个 TLV */
    public static SmgpTlv decode(ByteBuf buf) {
        SmgpTlv tlv = new SmgpTlv();
        tlv.tag = buf.readUnsignedShort();
        tlv.length = buf.readUnsignedShort();
        tlv.value = new byte[tlv.length];
        buf.readBytes(tlv.value);
        return tlv;
    }

    /**
     * 解析消息体末尾剩余的全部 TLV。
     * 若剩余字节不足以构成完整 TLV（网关非标实现），则丢弃剩余字节并返回已解析部分。
     */
    public static List<SmgpTlv> parseAll(ByteBuf buf) {
        List<SmgpTlv> list = new ArrayList<>();
        while (buf.readableBytes() >= 4) {
            int len = buf.getUnsignedShort(buf.readerIndex() + 2);
            if (buf.readableBytes() < 4 + len) {
                break;
            }
            list.add(decode(buf));
        }
        return list;
    }

    public static int calcLength(List<SmgpTlv> tlvs) {
        if (tlvs == null) return 0;
        int len = 0;
        for (SmgpTlv tlv : tlvs) {
            len += 4 + (tlv.value == null ? 0 : tlv.value.length);
        }
        return len;
    }

    /** 便捷方法：取 Value 的整数值（单字节） */
    public int getIntValue() {
        if (value == null || value.length == 0) return 0;
        return value[0] & 0xFF;
    }
}
