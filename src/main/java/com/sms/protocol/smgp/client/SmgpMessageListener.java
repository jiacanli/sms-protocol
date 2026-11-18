package com.sms.protocol.smgp.client;

import com.sms.protocol.smgp.message.SmgpActiveTestResp;
import com.sms.protocol.smgp.message.SmgpDeliver;
import com.sms.protocol.smgp.message.SmgpExitResp;
import com.sms.protocol.smgp.message.SmgpLoginResp;
import com.sms.protocol.smgp.message.SmgpReport;
import com.sms.protocol.smgp.message.SmgpSubmitResp;

/**
 * SMGP 客户端消息监听器。
 * 全部为 default 方法，业务方按需覆写。
 */
public interface SmgpMessageListener {

    /** 登录成功 */
    default void onLoginSuccess(SmgpClient client) {}

    /** 登录失败（status != 0），连接随后会被关闭 */
    default void onLoginFailed(SmgpLoginResp resp) {}

    /** 收到 SubmitResp（提交响应，含网关分配的 MsgID） */
    default void onSubmitResp(SmgpSubmitResp resp) {}

    /** 收到 Deliver（上行 MO 短信） */
    default void onDeliver(SmgpDeliver deliver) {}

    /** 收到状态报告（Deliver.IsReport=1） */
    default void onReport(SmgpReport report, SmgpDeliver deliver) {}

    /** 收到心跳响应 */
    default void onActiveTestResp(SmgpActiveTestResp resp) {}

    /** 收到注销响应（我方发送 Exit 后） */
    default void onExitResp(SmgpExitResp resp) {}

    /** 网关主动发起 Exit（要求断开），客户端已自动回复 ExitResp */
    default void onExitRequested(SmgpClient client) {}

    /** 连接被关闭（重连由 SmgpClient 按配置处理，此处仅通知） */
    default void onChannelClosed(SmgpClient client, Throwable cause) {}

    /** 连接异常 */
    default void onException(Throwable cause) {}
}
