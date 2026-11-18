package com.sms.protocol.cmpp20.message;

public final class CMPPCommand {

    private CMPPCommand() {
        // 工具类，禁止实例化
    }

    // ========== 请求命令 (Client -> Server) ==========
    /** 连接请求 */
    public static final int CMPP_CONNECT = 0x00000001;
    /** 终止连接请求 */
    public static final int CMPP_TERMINATE = 0x00000002;
    /** 提交短信请求 */
    public static final int CMPP_SUBMIT = 0x00000004;
    /** 短信下发状态报告请求 */
    public static final int CMPP_DELIVER = 0x00000005;
    /** 链路检测请求 */
    public static final int CMPP_ACTIVE_TEST = 0x00000008;

    // ========== 响应命令 (Server -> Client) ==========
    /** 连接响应 */
    public static final int CMPP_CONNECT_RESP = 0x80000001;
    /** 终止连接响应 */
    public static final int CMPP_TERMINATE_RESP = 0x80000002;
    /** 提交短信响应 */
    public static final int CMPP_SUBMIT_RESP = 0x80000004;
    /** 短信下发状态报告响应 */
    public static final int CMPP_DELIVER_RESP = 0x80000005;
    /** 链路检测响应 */
    public static final int CMPP_ACTIVE_TEST_RESP = 0x80000008;

    // ========== 扩展命令 (可选，按需保留) ==========
    /** 查询请求 */
    public static final int CMPP_QUERY = 0x00000006;
    /** 查询响应 */
    public static final int CMPP_QUERY_RESP = 0x80000006;
    /** 取消请求 */
    public static final int CMPP_CANCEL = 0x00000007;
    /** 取消响应 */
    public static final int CMPP_CANCEL_RESP = 0x80000007;

    /**
     * 判断是否为响应命令。
     * CMPP 规定响应命令字的最高位（bit 31）为 1。
     */
    public static boolean isResponse(int commandId) {
        return (commandId & 0x80000000) != 0;
    }

    /**
     * 获取对应的响应命令字。
     * 例如 CMPP_CONNECT -> CMPP_CONNECT_RESP。
     */
    public static int toResponse(int requestCommandId) {
        return requestCommandId | 0x80000000;
    }

    /**
     * 获取请求命令字（去掉响应标志位）。
     */
    public static int toRequest(int responseCommandId) {
        return responseCommandId & 0x7FFFFFFF;
    }

    /**
     * 根据命令字获取可读名称，便于日志排查。
     */
    public static String getName(int commandId) {
        switch (commandId) {
            case CMPP_CONNECT:          return "CMPP_CONNECT";
            case CMPP_TERMINATE:        return "CMPP_TERMINATE";
            case CMPP_SUBMIT:           return "CMPP_SUBMIT";
            case CMPP_DELIVER:          return "CMPP_DELIVER";
            case CMPP_ACTIVE_TEST:      return "CMPP_ACTIVE_TEST";
            case CMPP_CONNECT_RESP:     return "CMPP_CONNECT_RESP";
            case CMPP_TERMINATE_RESP:   return "CMPP_TERMINATE_RESP";
            case CMPP_SUBMIT_RESP:      return "CMPP_SUBMIT_RESP";
            case CMPP_DELIVER_RESP:     return "CMPP_DELIVER_RESP";
            case CMPP_ACTIVE_TEST_RESP: return "CMPP_ACTIVE_TEST_RESP";
            case CMPP_QUERY:            return "CMPP_QUERY";
            case CMPP_QUERY_RESP:       return "CMPP_QUERY_RESP";
            case CMPP_CANCEL:           return "CMPP_CANCEL";
            case CMPP_CANCEL_RESP:      return "CMPP_CANCEL_RESP";
            default:                    return "UNKNOWN(0x" + Integer.toHexString(commandId) + ")";
        }
    }
}
