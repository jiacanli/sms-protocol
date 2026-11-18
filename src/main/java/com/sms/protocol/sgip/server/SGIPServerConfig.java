package com.sms.protocol.sgip.server;

import lombok.Data;

/**
 * SGIP 服务端配置
 * 用于 SP 端监听 SMG 的主动连接，接收 Deliver（上行短信）和 Report（状态报告）
 */
@Data
public class SGIPServerConfig {

    /** 监听端口 */
    private int port = 8890;

    /** 服务端节点号（SP 节点号，响应时作为源节点号） */
    private long sourceNodeId;

    /** 允许连接的对端节点号（SMG 节点号），-1 表示不校验 */
    private long allowedClientNodeId = -1;

    /** 登录账号（SMG 连接 SP 时使用的账号） */
    private String loginName;

    /** 登录密码 */
    private String loginPassword;

    /** 读空闲超时（秒），超过后关闭连接 */
    private int readerIdleSeconds = 60;

    /** 写空闲超时（秒），0 表示不主动发心跳 */
    private int writerIdleSeconds = 0;

    /** 连接超时毫秒 */
    private int connectTimeoutMillis = 5000;

    /** 业务线程池大小，0 表示使用 IO 线程直接处理 */
    private int businessThreads = 0;

    public SGIPServerConfig() {
    }

    public SGIPServerConfig(int port, long sourceNodeId, String loginName, String loginPassword) {
        this.port = port;
        this.sourceNodeId = sourceNodeId;
        this.loginName = loginName;
        this.loginPassword = loginPassword;
    }
}
