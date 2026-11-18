package com.sms.protocol.sgip.codec;



import com.sms.protocol.sgip.client.SGIPClient;
import com.sms.protocol.sgip.constant.SGIPCommand;
import com.sms.protocol.sgip.message.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SGIPClientHandler extends SimpleChannelInboundHandler<SGIPMessage> {

    private final SGIPClient client;

    public SGIPClientHandler(SGIPClient client) {
        this.client = client;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("SGIP 连接已建立: {}", ctx.channel().remoteAddress());
        // 连接建立后自动登录
        client.sendBind(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("SGIP 连接已断开");
        client.onDisconnected();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SGIPMessage msg) {
        int cmd = msg.getCommandId();
        switch (cmd) {
            // 登录响应
            case SGIPCommand.SGIP_BIND_RESP:
                SGIPBindResp bindResp = (SGIPBindResp) msg;
                if (bindResp.getResult() == 0) {
                    log.info("SGIP 登录成功");
                    client.onLoginSuccess(ctx.channel());
                } else {
                    log.error("SGIP 登录失败, 错误码: {}", bindResp.getResult());
                    ctx.close();
                }
                break;

            // 提交响应
            case SGIPCommand.SGIP_SUBMIT_RESP:
                SGIPSubmitResp submitResp = (SGIPSubmitResp) msg;
                log.debug("收到提交响应: seq={}, result={}",
                        msg.getSequenceKey(), submitResp.getResult());
                client.onSubmitResp(submitResp);
                break;

            // 上行短信推送
            case SGIPCommand.SGIP_DELIVER:
                SGIPDeliver deliver = (SGIPDeliver) msg;
                log.debug("收到上行短信: 来自={}, 长度={}",
                        deliver.getSrcTerminalId(), deliver.getMsgLength());
                // 业务处理
                client.onDeliver(deliver);
                // 必须回复响应
                SGIPDeliverResp deliverResp = new SGIPDeliverResp();
                deliverResp.setHeader(buildRespHeader(deliver.getHeader(), SGIPCommand.SGIP_DELIVER_RESP));
                deliverResp.setResult(0);
                ctx.writeAndFlush(deliverResp);
                break;

            // 状态报告推送
            case SGIPCommand.SGIP_REPORT:
                SGIPReport report = (SGIPReport) msg;
                log.debug("收到状态报告: 号码={}, 状态={}",
                        report.getUserNumber(), report.getState());
                // 业务处理
                client.onReport(report);
                // 必须回复响应
                SGIPReportResp reportResp = new SGIPReportResp();
                reportResp.setHeader(buildRespHeader(report.getHeader(), SGIPCommand.SGIP_REPORT_RESP));
                reportResp.setResult(0);
                ctx.writeAndFlush(reportResp);
                break;

            // 网关心跳请求
            case SGIPCommand.SGIP_ACTIVE_TEST:
                log.debug("收到网关心跳请求，回复响应");
                SGIPActiveTestResp activeTestResp = new SGIPActiveTestResp();
                activeTestResp.setHeader(buildRespHeader(msg.getHeader(), SGIPCommand.SGIP_ACTIVE_TEST_RESP));
                ctx.writeAndFlush(activeTestResp);
                break;

            // 心跳响应
            case SGIPCommand.SGIP_ACTIVE_TEST_RESP:
                log.debug("收到心跳响应");
                break;

            // 网关断开请求
            case SGIPCommand.SGIP_TERMINATE:
                log.info("收到网关断开请求，回复并关闭连接");
                SGIPTerminateResp terminateResp = new SGIPTerminateResp();
                terminateResp.setHeader(buildRespHeader(msg.getHeader(), SGIPCommand.SGIP_TERMINATE_RESP));
                ctx.writeAndFlush(terminateResp);
                ctx.close();
                break;

            // 断开响应
            case SGIPCommand.SGIP_TERMINATE_RESP:
                log.info("收到断开响应，关闭连接");
                ctx.close();
                break;

            default:
                log.warn("未处理的命令类型: 0x{}", Integer.toHexString(cmd));
                break;
        }
    }

    /**
     * 构建响应消息头，序列号与请求保持一致
     */
    private SGIPHeader buildRespHeader(SGIPHeader reqHeader, int respCmd) {
        SGIPHeader header = new SGIPHeader();
        header.setCommandId(respCmd);
        header.setSourceNodeId(reqHeader.getDestNodeId());
        header.setDestNodeId(reqHeader.getSourceNodeId());
        header.setSequenceNumber(reqHeader.getSequenceNumber());
        return header;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.debug("链路空闲，发送心跳");
            client.sendActiveTest(ctx.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("SGIP 客户端异常", cause);
        ctx.close();
    }
}

