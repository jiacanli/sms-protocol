package com.sms.protocol.utils;


/**
 * CMPP 2.0 长短信相关常量
 */
public final class CMPPLongSmsConstants {

    private CMPPLongSmsConstants() {}

    /** 单条短信 Msg_Content 最大长度 */
    public static final int MAX_SINGLE_MSG_LENGTH = 140;

    /**
     * 长短信分片正文最大字节数（128 字节）。
     * <p>
     * 与移动网关约定按 64 个 UCS2 汉字（128 字节）切分：
     * 加上 6 字节 UDH 后为 134 字节，仍在 140 字节上限内；
     * 7 字节 UDH 时为 135 字节，同样安全。
     */
    public static final int MAX_FRAGMENT_PAYLOAD_BYTES = 128;

    /** 6 字节 UDH 协议头长度 */
    public static final int UDH_HEADER_LENGTH_6 = 6;

    /** 7 字节 UDH 协议头长度 */
    public static final int UDH_HEADER_LENGTH_7 = 7;

    /** Msg_Fmt: ASCII */
    public static final int MSG_FMT_ASCII = 0;
    /** Msg_Fmt: UCS2 (中文推荐) */
    public static final int MSG_FMT_UCS2 = 8;
    /** Msg_Fmt: 含 GB 汉字 */
    public static final int MSG_FMT_GBK = 15;

    /** IEI: 拼接短信标识 */
    public static final int IEI_CONCATENATED_8BIT_REF = 0x00;

    /** UCS2 字符集名 */
    public static final String CHARSET_UCS2 = "iso-10646-ucs-2";
    /** GBK 字符集名 */
    public static final String CHARSET_GBK = "GBK";
    /** ISO-8859-1，用于 byte[] 与 String 互转不丢字节 */
    public static final String CHARSET_ISO88591 = "ISO-8859-1";
}