package com.sms.protocol.smgp.client;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.smgp.message.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * SMGP 客户端业务处理器：登录、心跳、响应分发、Deliver 应答。
 * 登录账号/密码/地址等均来自 {@link SmgpClientConfig}，不再硬编码。
 */
@Slf4j
public class SmgpClientHandler extends SimpleChannelInboundHandler<SmgpMessage> {

    private final SmgpClientConfig config;
    private final SmgpClient client;

    public SmgpClientHandler(SmgpClientConfig config, SmgpClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("SMGP 连接已建立: {}", ctx.channel().remoteAddress());
        client.sendLogin(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("SMGP 连接已断开");
        client.onDisconnected(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SmgpMessage msg) {
        int cmd = msg.getCommandId();
        switch (cmd) {

            // ---- 登录响应 ----
            case SmgpConstants.SMGP_LOGIN_RESP: {
                SmgpLoginResp loginResp = (SmgpLoginResp) msg;
                if (loginResp.getStatus() == 0) {
                    log.info("SMGP 登录成功, serverVersion={}", loginResp.getServerVersion() & 0xFF);
                    client.onLoginSuccess(loginResp);
                } else {
                    log.error("SMGP 登录失败, status={}", loginResp.getStatus());
                    client.onLoginFailed(loginResp);
                    ctx.close();
                }
                break;
            }

            // ---- 提交响应 ----
            case SmgpConstants.SMGP_SUBMIT_RESP:
                client.onSubmitResp((SmgpSubmitResp) msg);
                break;

            // ---- 网关下发（上行 MO + 状态报告），协议强制回 DeliverResp ----
            case SmgpConstants.SMGP_DELIVER: {
                SmgpDeliver deliver = (SmgpDeliver) msg;

                SmgpDeliverResp deliverResp = new SmgpDeliverResp();
                deliverResp.getHeader().setSequenceId(deliver.getHeader().getSequenceId());
                deliverResp.setMsgId(deliver.getMsgId());
                deliverResp.setMsgIdLong(deliver.getMsgIdLong()); // 保持 MsgID 编码格式对称
                deliverResp.setStatus(0);
                ctx.writeAndFlush(deliverResp);

                if (deliver.getIsReport() == 1 && deliver.getReport() != null) {
                    client.onReport(deliver.getReport(), deliver);
                } else {
                    client.onDeliver(deliver);
                }
                break;
            }

            // ---- 心跳响应 ----
            case SmgpConstants.SMGP_ACTIVE_TEST_RESP:
                client.onActiveTestResp((SmgpActiveTestResp) msg);
                break;

            // ---- 注销响应（我方发送 Exit 后） ----
            case SmgpConstants.SMGP_EXIT_RESP:
                client.onExitResp((SmgpExitResp) msg);
                break;

            // ---- 网关主动发起 Exit，协议要求回 ExitResp ----
            case SmgpConstants.SMGP_EXIT: {
                log.info("网关主动发起 Exit，回复 ExitResp");
                SmgpExitResp exitResp = new SmgpExitResp();
                exitResp.getHeader().setSequenceId(msg.getHeader().getSequenceId());
                ctx.writeAndFlush(exitResp);
                client.onExitRequested();
                break;
            }

            default:
                log.warn("未处理的命令类型: 0x{}", Integer.toHexString(cmd));
                break;
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.debug("读空闲，发送 ActiveTest 心跳");
            client.sendActiveTest(ctx.channel());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("SMGP 客户端异常", cause);
        client.onException(cause);
        ctx.close();
    }
}
