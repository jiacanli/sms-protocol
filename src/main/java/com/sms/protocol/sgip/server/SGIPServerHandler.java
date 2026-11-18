package com.sms.protocol.sgip.server;

import com.sms.protocol.sgip.constant.SGIPCommand;
import com.sms.protocol.sgip.message.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * SGIP 服务端业务处理器
 * 处理 SMG 主动连接后推送的 Deliver / Report，以及 Bind / ActiveTest / Terminate
 */
@Slf4j
public class SGIPServerHandler extends SimpleChannelInboundHandler<SGIPMessage> {

    private final SGIPServerConfig config;
    private final SGIPServerListener listener;

    /** 对端是否已通过登录验证 */
    private volatile boolean authenticated = false;
    /** 对端节点号（登录后从 Bind 消息头获取） */
    private volatile long clientNodeId = -1;

    public SGIPServerHandler(SGIPServerConfig config, SGIPServerListener listener) {
        this.config = config;
        this.listener = listener;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("SGIP 服务端收到新连接: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("SGIP 服务端连接断开: {}, clientNodeId={}", ctx.channel().remoteAddress(), clientNodeId);
        if (listener != null) {
            listener.onClientDisconnected(clientNodeId, String.valueOf(ctx.channel().remoteAddress()));
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SGIPMessage msg) {
        int cmd = msg.getCommandId();

        // 未登录时只允许 Bind，其他命令直接拒绝
        if (!authenticated && cmd != SGIPCommand.SGIP_BIND) {
            log.warn("未登录的连接尝试发送命令: 0x{}, 远程地址={}",
                    Integer.toHexString(cmd), ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        switch (cmd) {
            // 登录请求
            case SGIPCommand.SGIP_BIND:
                handleBind(ctx, (SGIPBind) msg);
                break;

            // 上行短信推送
            case SGIPCommand.SGIP_DELIVER:
                handleDeliver(ctx, (SGIPDeliver) msg);
                break;

            // 状态报告推送
            case SGIPCommand.SGIP_REPORT:
                handleReport(ctx, (SGIPReport) msg);
                break;

            // 心跳请求
            case SGIPCommand.SGIP_ACTIVE_TEST:
                handleActiveTest(ctx, msg);
                break;

            // 断开请求
            case SGIPCommand.SGIP_TERMINATE:
                handleTerminate(ctx, msg);
                break;

            // SMG 提交短信（少见，保留处理）
            case SGIPCommand.SGIP_SUBMIT:
                log.warn("收到 SMG 主动 Submit，暂不处理: seq={}", msg.getSequenceKey());
                break;

            default:
                log.warn("SGIP 服务端未处理的命令: 0x{}", Integer.toHexString(cmd));
                break;
        }
    }

    // ==================================================================
    //                       各命令处理方法
    // ==================================================================

    /**
     * 处理登录请求：验证账号密码，回复 Bind_Resp
     */
    private void handleBind(ChannelHandlerContext ctx, SGIPBind bind) {
        String remoteAddr = String.valueOf(ctx.channel().remoteAddress());
        log.info("收到 Bind 请求: loginType={}, name={}, 远程={}",
                bind.getLoginType(), bind.getName(), remoteAddr);

        SGIPBindResp resp = new SGIPBindResp();
        resp.setHeader(buildRespHeader(bind.getHeader(), SGIPCommand.SGIP_BIND_RESP));

        // 验证账号密码
        boolean nameOk = config.getLoginName() == null || config.getLoginName().equals(bind.getName());
        boolean pwdOk = config.getLoginPassword() == null || config.getLoginPassword().equals(bind.getPassword());

        if (nameOk && pwdOk) {
            resp.setResult(0); // 成功
            authenticated = true;
            clientNodeId = bind.getHeader().getSourceNodeId();
            log.info("SGIP 登录成功: clientNodeId={}, 远程={}", clientNodeId, remoteAddr);

            if (listener != null) {
                listener.onClientConnected(clientNodeId, remoteAddr);
            }
        } else {
            resp.setResult(1); // 非法账号/密码
            log.warn("SGIP 登录失败: name={}, 远程={}", bind.getName(), remoteAddr);
            if (listener != null) {
                listener.onLoginFailed(remoteAddr, "账号或密码错误");
            }
        }

        ctx.writeAndFlush(resp);

        // 登录失败后关闭连接
        if (!authenticated) {
            ctx.close();
        }
    }

    /**
     * 处理上行短信：回复 Deliver_Resp，回调业务层
     */
    private void handleDeliver(ChannelHandlerContext ctx, SGIPDeliver deliver) {
        log.debug("收到 Deliver: 来自={}, 长度={}, seq={}",
                deliver.getSrcTerminalId(), deliver.getMsgLength(), deliver.getSequenceKey());

        // 协议强制：必须回复 Deliver_Resp，序列号与请求一致
        SGIPDeliverResp resp = new SGIPDeliverResp();
        resp.setHeader(buildRespHeader(deliver.getHeader(), SGIPCommand.SGIP_DELIVER_RESP));
        resp.setResult(0);
        ctx.writeAndFlush(resp);

        // 回调业务层
        if (listener != null) {
            try {
                listener.onDeliver(deliver);
            } catch (Exception e) {
                log.error("业务层处理 Deliver 异常", e);
            }
        }
    }

    /**
     * 处理状态报告：回复 Report_Resp，回调业务层
     */
    private void handleReport(ChannelHandlerContext ctx, SGIPReport report) {
        log.debug("收到 Report: 号码={}, 状态={}, seq={}",
                report.getUserNumber(), report.getState(), report.getSequenceKey());

        // 协议强制：必须回复 Report_Resp，序列号与请求一致
        SGIPReportResp resp = new SGIPReportResp();
        resp.setHeader(buildRespHeader(report.getHeader(), SGIPCommand.SGIP_REPORT_RESP));
        resp.setResult(0);
        ctx.writeAndFlush(resp);

        // 回调业务层
        if (listener != null) {
            try {
                listener.onReport(report);
            } catch (Exception e) {
                log.error("业务层处理 Report 异常", e);
            }
        }
    }

    /**
     * 处理心跳请求：回复 ActiveTest_Resp
     */
    private void handleActiveTest(ChannelHandlerContext ctx, SGIPMessage msg) {
        log.debug("收到心跳请求, 远程={}", ctx.channel().remoteAddress());
        SGIPActiveTestResp resp = new SGIPActiveTestResp();
        resp.setHeader(buildRespHeader(msg.getHeader(), SGIPCommand.SGIP_ACTIVE_TEST_RESP));
        ctx.writeAndFlush(resp);
    }

    /**
     * 处理断开请求：回复 Terminate_Resp 并关闭连接
     */
    private void handleTerminate(ChannelHandlerContext ctx, SGIPMessage msg) {
        log.info("收到断开请求, 远程={}", ctx.channel().remoteAddress());
        SGIPTerminateResp resp = new SGIPTerminateResp();
        resp.setHeader(buildRespHeader(msg.getHeader(), SGIPCommand.SGIP_TERMINATE_RESP));
        ctx.writeAndFlush(resp);
        ctx.close();
    }

    // ==================================================================
    //                       辅助方法
    // ==================================================================

    /**
     * 构建响应消息头
     * SGIP 序列号为三段式，响应时需交换源/目的节点号，流水号保持一致
     */
    private SGIPHeader buildRespHeader(SGIPHeader reqHeader, int respCmd) {
        SGIPHeader header = new SGIPHeader();
        header.setCommandId(respCmd);
        header.setSourceNodeId(config.getSourceNodeId());
        header.setDestNodeId(reqHeader.getSourceNodeId());
        header.setSequenceNumber(reqHeader.getSequenceNumber());
        return header;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleEvent = (IdleStateEvent) evt;
            switch (idleEvent.state()) {
                case READER_IDLE:
                    log.warn("读空闲超时，关闭连接: 远程={}", ctx.channel().remoteAddress());
                    ctx.close();
                    break;
                case WRITER_IDLE:
                    // 写空闲时主动发心跳（如果配置了 writerIdleSeconds > 0）
                    if (authenticated) {
                        log.debug("写空闲，主动发送心跳: 远程={}", ctx.channel().remoteAddress());
                        SGIPActiveTest activeTest = new SGIPActiveTest();
                        SGIPHeader header = new SGIPHeader();
                        header.setCommandId(SGIPCommand.SGIP_ACTIVE_TEST);
                        header.setSourceNodeId(config.getSourceNodeId());
                        header.setDestNodeId(clientNodeId);
                        header.setSequenceNumber(System.currentTimeMillis() & 0xFFFFFFFFL);
                        activeTest.setHeader(header);
                        ctx.writeAndFlush(activeTest);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("SGIP 服务端异常, 远程={}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
