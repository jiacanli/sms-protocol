package com.sms.protocol.sgip.utils;

/**
 * SGIP 长短信相关常量
 * 与 CMPPLongSmsConstants 保持一致的协议标准（UDH 遵循 GSM 03.40）
 */
public final class SGIPLongSmsConstants {

    private SGIPLongSmsConstants() {}

    /** 单条短信 Msg_Content 最大长度（140 字节） */
    public static final int MAX_SINGLE_MSG_LENGTH = 140;

    /** 6 字节 UDH 协议头长度（8 位参考号，最多 255 片） */
    public static final int UDH_HEADER_LENGTH_6 = 6;

    /** 7 字节 UDH 协议头长度（16 位参考号，最多 65535 片） */
    public static final int UDH_HEADER_LENGTH_7 = 7;

    /** Msg_Fmt: ASCII */
    public static final int MSG_FMT_ASCII = 0;
    /** Msg_Fmt: UCS2（中文推荐） */
    public static final int MSG_FMT_UCS2 = 8;
    /** Msg_Fmt: GBK */
    public static final int MSG_FMT_GBK = 15;

    /** IEI: 拼接短信（8位参考号） */
    public static final int IEI_CONCATENATED_8BIT_REF = 0x00;
    /** IEI: 拼接短信（16位参考号） */
    public static final int IEI_CONCATENATED_16BIT_REF = 0x08;

    /** UCS2 字符集名 */
    public static final String CHARSET_UCS2 = "iso-10646-ucs-2";
    /** GBK 字符集名 */
    public static final String CHARSET_GBK = "GBK";
}
