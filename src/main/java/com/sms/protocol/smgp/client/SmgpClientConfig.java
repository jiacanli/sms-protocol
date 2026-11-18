package com.sms.protocol.smgp.client;

import com.sms.protocol.smgp.SmgpConstants;
import lombok.Data;

/**
 * SMGP 客户端配置
 */
@Data
public class SmgpClientConfig {

    /** 网关地址 */
    private String host = "127.0.0.1";

    /** 网关端口 */
    private int port = 8890;

    /** SP 企业代码（ClientID，8 字节定长字段） */
    private String clientId;

    /** 共享密钥（用于计算 AuthenticatorClient） */
    private String secret;

    /** 登录类型：1=短连接，2=长连接（推荐） */
    private byte loginMode = SmgpConstants.LOGIN_MODE_LONG_CONN;

    /** 业务代码（ServiceID，10 字节），便捷发送时的默认值 */
    private String serviceId = "test";

    /** 接入号（SrcTermID，21 字节），便捷发送时的默认值 */
    private String srcId;

    /** 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** 读空闲时间（秒），超过则发送 ActiveTest 心跳 */
    private int readerIdleSeconds = 30;

    /** 是否断线自动重连 */
    private boolean reconnectEnabled = true;

    /** 重连间隔（秒） */
    private int reconnectIntervalSeconds = 5;

    /** 登录等待超时（秒） */
    private long loginTimeoutSeconds = 10;

    /** 等待 SubmitResp 的超时（毫秒），用于 sendSubmit/sendSms 的异步结果 */
    private long submitTimeoutMillis = 30000;
}
