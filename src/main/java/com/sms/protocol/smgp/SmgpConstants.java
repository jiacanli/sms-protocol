package com.sms.protocol.smgp;

public class SmgpConstants {
    public static final String PROTOCOL_NAME = "SMGP";
    /**
     * 协议版本号：高 4 位主版本，低 4 位次版本。
     * SMGP 3.0 => 0x30（2001 版为 0x10 语义，3.0 规范要求 0x30，实际对接以网关为准）
     */
    public static final int PROTOCOL_VERSION = 0x30;
    public static final int HEADER_LENGTH = 12;

    // ========== Command ID 定义（中国电信 SMGP V3.0.3） ==========
    public static final int SMGP_LOGIN = 0x00000001;           // 登录请求
    public static final int SMGP_LOGIN_RESP = 0x80000001;      // 登录响应
    public static final int SMGP_SUBMIT = 0x00000002;          // 提交短信
    public static final int SMGP_SUBMIT_RESP = 0x80000002;     // 提交响应
    public static final int SMGP_DELIVER = 0x00000003;         // SMGW 下发短消息（MO 上行/状态报告）
    public static final int SMGP_DELIVER_RESP = 0x80000003;    // 下发响应
    public static final int SMGP_ACTIVE_TEST = 0x00000004;     // 链路检测（无消息体）
    public static final int SMGP_ACTIVE_TEST_RESP = 0x80000004;// 链路检测响应（无消息体）
    public static final int SMGP_FORWARD = 0x00000005;         // SMGW 之间转发短消息
    public static final int SMGP_FORWARD_RESP = 0x80000005;    // 转发响应
    public static final int SMGP_EXIT = 0x00000006;            // 注销请求（无消息体）
    public static final int SMGP_EXIT_RESP = 0x80000006;       // 注销响应（无消息体）
    public static final int SMGP_QUERY = 0x00000007;           // 查询短消息发送状态（可选）
    public static final int SMGP_QUERY_RESP = 0x80000007;      // 查询响应

    // ========== LoginMode ==========
    public static final byte LOGIN_MODE_SHORT_CONN = 1;   // 短连接
    public static final byte LOGIN_MODE_LONG_CONN = 2;    // 长连接

    // ========== MsgType（Submit/Deliver 短消息类型） ==========
    public static final byte MSG_TYPE_MO = 0;   // MO 消息（终端 -> SP，Deliver 携带）
    public static final byte MSG_TYPE_MT = 6;   // MT 消息（SP -> 终端，Submit 使用）
    public static final byte MSG_TYPE_P2P = 7;  // 点对点消息

    // ========== MsgFormat（短消息格式，字节 6.2.16） ==========
    public static final byte MSG_FORMAT_ASCII = 0;   // ASCII
    public static final byte MSG_FORMAT_BINARY = 4;  // 二进制
    public static final byte MSG_FORMAT_UCS2 = 8;    // UCS2（UTF-16BE），中文短信常用
    public static final byte MSG_FORMAT_GBK = 15;    // GBK/GB18030（电信扩展）

    // ========== 状态报告 Stat 取值 ==========
    public static final String STAT_DELIVRD = "DELIVRD";  // 成功送达
    public static final String STAT_EXPIRED = "EXPIRED";  // 有效期过期
    public static final String STAT_DELETED = "DELETED";  // 已被删除
    public static final String STAT_UNDELIV = "UNDELIV";  // 未送达
    public static final String STAT_ACCEPTD = "ACCEPTD";  // 已接受
    public static final String STAT_UNKNOWN = "UNKNOWN";  // 未知

    // ========== 可选参数（TLV） ==========
    // 可选参数采用 TLV（Tag 2 字节 + Length 2 字节 + Value N 字节）形式，见规范 6.3 节。
    // Tag 编号经多个独立开源实现交叉验证（go_smgp / gosms / gosmgp），与规范章节顺序一致；
    // 实际对接仍以网关提供的文档为准，如不一致只需修改本处常量。
    public static final int TLV_TP_PID = 0x0001;               // GSM 协议类型（TP_pid）
    public static final int TLV_TP_UDHI = 0x0002;              // 用户数据头指示（长短信置 1）
    public static final int TLV_LINK_ID = 0x0003;              // 交易标识（关联同一组分片）
    public static final int TLV_CHARGE_USER_TYPE = 0x0004;     // 计费用户类型
    public static final int TLV_CHARGE_TERM_TYPE = 0x0005;     // 计费用户号码类型
    public static final int TLV_CHARGE_TERM_PSEUDO = 0x0006;   // 计费用户伪码
    public static final int TLV_DEST_TERM_TYPE = 0x0007;       // 接收方号码类型
    public static final int TLV_DEST_TERM_PSEUDO = 0x0008;     // 接收方伪码
    public static final int TLV_PK_TOTAL = 0x0009;             // 相同 MsgID 的消息总条数
    public static final int TLV_PK_NUMBER = 0x000A;            // 相同 MsgID 的消息序号
    public static final int TLV_SUBMIT_MSG_TYPE = 0x000B;      // SP 发送的消息类型
    public static final int TLV_SP_DEAL_RESULT = 0x000C;       // SP 对消息的处理结果
    public static final int TLV_MSG_SRC = 0x000D;              // 信息内容的来源
    public static final int TLV_SRC_TERM_TYPE = 0x000E;        // 发送方号码类型
    public static final int TLV_SRC_TERM_PSEUDO = 0x000F;      // 发送方伪码
}
