package com.sms.protocol.sgip.server;

import com.sms.protocol.sgip.message.SGIPDeliver;
import com.sms.protocol.sgip.message.SGIPReport;

/**
 * SGIP 服务端业务回调接口
 * 使用方实现此接口，处理 SMG 推送的上行短信和状态报告
 */
public interface SGIPServerListener {

    /**
     * 收到上行短信（Deliver）
     * 注意：此方法在 Netty IO 线程中调用，如有耗时操作请自行异步处理
     *
     * @param deliver 上行短信消息
     */
    void onDeliver(SGIPDeliver deliver);

    /**
     * 收到状态报告（Report）
     * 注意：此方法在 Netty IO 线程中调用，如有耗时操作请自行异步处理
     *
     * @param report 状态报告消息
     */
    void onReport(SGIPReport report);

    /**
     * 客户端连接成功（已通过 Bind 验证）
     *
     * @param clientNodeId 对端节点号
     * @param remoteAddr   对端地址
     */
    default void onClientConnected(long clientNodeId, String remoteAddr) {
    }

    /**
     * 客户端连接断开
     *
     * @param clientNodeId 对端节点号（未登录时为 -1）
     * @param remoteAddr   对端地址
     */
    default void onClientDisconnected(long clientNodeId, String remoteAddr) {
    }

    /**
     * 登录验证失败
     *
     * @param remoteAddr 对端地址
     * @param reason     失败原因
     */
    default void onLoginFailed(String remoteAddr, String reason) {
    }
}
