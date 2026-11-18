package com.sms.protocol.utils;

/**
 * SMGP 3.0 长短信相关常量
 */
public final class SmgpLongSmsConstants {

    private SmgpLongSmsConstants() {}

    /** 单条短信 Msg_Content 最大字节数（GSM 无线侧 140 字节上限） */
    public static final int MAX_SINGLE_MSG_LENGTH = 140;

    /** 6 字节 UDH 协议头长度 */
    public static final int UDH_HEADER_LENGTH_6 = 6;

    /** 7 字节 UDH 协议头长度（16 位参考号） */
    public static final int UDH_HEADER_LENGTH_7 = 7;

    /** UDH 中 total 字段为 1 字节，最多支持的分片数 */
    public static final int MAX_FRAGMENT_COUNT = 255;

    /** MsgFormat: ASCII */
    public static final int MSG_FMT_ASCII = 0;
    /** MsgFormat: UCS2（中文推荐，每片 67 字符） */
    public static final int MSG_FMT_UCS2 = 8;
    /** MsgFormat: 含 GB 汉字 */
    public static final int MSG_FMT_GBK = 15;

    /** IEI: 拼接短信标识（8 位参考号，6 字节 UDH） */
    public static final int IEI_CONCATENATED_8BIT_REF = 0x00;

    /** IEI: 拼接短信标识（16 位参考号，7 字节 UDH） */
    public static final int IEI_CONCATENATED_16BIT_REF = 0x08;

    /** UCS2 字符集名（UTF-16BE，与 SmgpSubmit.setMsgText 保持一致） */
    public static final String CHARSET_UCS2 = "UTF-16BE";
    /** GBK 字符集名 */
    public static final String CHARSET_GBK = "GBK";
}
