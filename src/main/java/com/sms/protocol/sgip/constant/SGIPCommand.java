package com.sms.protocol.sgip.constant;


/**
 * SGIP 1.2 协议命令常量
 */
public final class SGIPCommand {

    private SGIPCommand() {}

    // ==================== 请求命令 ====================
    /** 登录请求 */
    public static final int SGIP_BIND = 0x00000001;
    /** 短信提交请求 */
    public static final int SGIP_SUBMIT = 0x00000003;
    /** 上行短信推送（网关→SP） */
    public static final int SGIP_DELIVER = 0x00000004;
    /** 状态报告推送（网关→SP） */
    public static final int SGIP_REPORT = 0x00000005;
    /** 心跳请求 */
    public static final int SGIP_ACTIVE_TEST = 0x00000006;
    /** 断开连接请求 */
    public static final int SGIP_TERMINATE = 0x00000007;

    // ==================== 响应命令 ====================
    /** 登录响应 */
    public static final int SGIP_BIND_RESP = 0x80000001;
    /** 短信提交响应 */
    public static final int SGIP_SUBMIT_RESP = 0x80000003;
    /** 上行短信响应 */
    public static final int SGIP_DELIVER_RESP = 0x80000004;
    /** 状态报告响应 */
    public static final int SGIP_REPORT_RESP = 0x80000005;
    /** 心跳响应 */
    public static final int SGIP_ACTIVE_TEST_RESP = 0x80000006;
    /** 断开连接响应 */
    public static final int SGIP_TERMINATE_RESP = 0x80000007;
}
