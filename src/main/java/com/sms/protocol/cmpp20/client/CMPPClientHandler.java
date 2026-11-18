package com.sms.protocol.cmpp20.client;


import com.sms.protocol.cmpp20.message.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CMPPClientHandler extends SimpleChannelInboundHandler<CMPPMessage> {

    private final CMPPClient client;

    public CMPPClientHandler(CMPPClient client) {
        this.client = client;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("CMPP 连接已建立: {}", ctx.channel().remoteAddress());
        // 建立连接后立即发送登录
        client.sendConnect(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("CMPP 连接已断开");
        client.onDisconnected();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CMPPMessage msg) {
        int cmd = msg.getCommandId();
        switch (cmd) {
            // 登录响应
            case CMPPCommand.CMPP_CONNECT_RESP:
                CMPPConnectResp connectResp = (CMPPConnectResp) msg;
                if (connectResp.getStatus() == 0) {
                    log.info("CMPP 登录成功");
                    client.onLoginSuccess(ctx.channel());
                } else {
                    log.error("CMPP 登录失败, status={}", connectResp.getStatus());
                    ctx.close();
                }
                break;

            // 短信提交响应
            case CMPPCommand.CMPP_SUBMIT_RESP:
                CMPPSubmitResp submitResp = (CMPPSubmitResp) msg;
                log.debug("收到 Submit 响应: seq={}, msgId={}, result={}",
                        submitResp.getSequenceId(), submitResp.getMsgId(), submitResp.getResult());
                client.onSubmitResp(submitResp);
                break;

            // 网关推送消息（上行短信 + 状态报告）
            case CMPPCommand.CMPP_DELIVER:
                CMPPDeliver deliver = (CMPPDeliver) msg;
                log.debug("收到 Deliver , {}",deliver);
                // 协议强制：必须回复 DELIVER_RESP，序列号与请求一致
                CMPPDeliverResp deliverResp = new CMPPDeliverResp();
                CMPPHeader deliverRespHeader = new CMPPHeader();
                deliverRespHeader.setCommandId(CMPPCommand.CMPP_DELIVER_RESP);
                deliverRespHeader.setSequenceId(deliver.getSequenceId());
                deliverResp.setHeader(deliverRespHeader);
                deliverResp.setMsgId(deliver.getMsgId());
                deliverResp.setResult(0); // 0 表示接收成功
                ctx.writeAndFlush(deliverResp);
                break;

            // 心跳响应（我方发心跳，网关回的响应）
            case CMPPCommand.CMPP_ACTIVE_TEST_RESP:
                log.debug("收到心跳响应");
                break;

            // 网关主动发的心跳请求
            case CMPPCommand.CMPP_ACTIVE_TEST:
                log.debug("收到网关心跳请求，回复响应");
                // 回复心跳响应，序列号与请求一致
                CMPPActiveTestResp activeTestResp = new CMPPActiveTestResp();
                CMPPHeader activeRespHeader = new CMPPHeader();
                activeRespHeader.setCommandId(CMPPCommand.CMPP_ACTIVE_TEST_RESP);
                activeRespHeader.setSequenceId(msg.getSequenceId());
                activeTestResp.setHeader(activeRespHeader);
                ctx.writeAndFlush(activeTestResp);
                break;

            // 网关主动发起断开请求
            case CMPPCommand.CMPP_TERMINATE:
                log.info("收到网关断开请求，关闭连接");
                ctx.close();
                break;


            default:
                log.warn("未处理的消息类型: 0x{}", Integer.toHexString(cmd));
                break;
    }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.debug("读空闲，发送心跳");
            client.sendActiveTest(ctx.channel());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("CMPP 客户端异常", cause);
        ctx.close();
    }
}
