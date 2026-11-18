package com.sms.protocol.cmpp20.client;

import lombok.Data;

@Data
public class CMPPClientConfig {

    private String host;
    private int port;

    /** 登录信息 */
    private String sourceAddr;    // 6 字节，SP 的企业代码
    private String sharedSecret;  // 6 字节，共享密钥

    /** 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** 读空闲时间（秒），超过则发心跳 */
    private int readerIdleSeconds = 30;

    /** 最大分片发送间隔（毫秒），避免瞬间刷屏 */
    private long splitSendIntervalMs = 20;

    // getter / setter 省略
}
