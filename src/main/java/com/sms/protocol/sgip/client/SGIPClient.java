package com.sms.protocol.sgip.client;

import com.sms.protocol.sgip.codec.SGIPClientHandler;
import com.sms.protocol.sgip.codec.SGIPDecoder;
import com.sms.protocol.sgip.codec.SGIPEncoder;
import com.sms.protocol.sgip.constant.SGIPCommand;
import com.sms.protocol.sgip.message.*;
import com.sms.protocol.sgip.utils.SGIPLongSmsUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SGIP 1.2 客户端核心类
 * 方法签名与 Handler 调用完全对应，提供重载兼顾内部调用与业务调用
 */
@Slf4j
public class SGIPClient {

    // ==================== 运营商配置参数 ====================
    private final String host;
    private final int port;
    private final long sourceNodeId;
    private final long destNodeId;
    private final String loginName;
    private final String loginPassword;
    private final String spId;
    private final String spNumber;
    private final String defaultServiceId;

    // ==================== Netty 组件 ====================
    private Bootstrap bootstrap;
    private EventLoopGroup workerGroup;
    @Getter
    private volatile Channel channel;

    // ==================== 状态与序列号 ====================
    private final AtomicInteger sequenceGenerator = new AtomicInteger(1);
    @Getter
    private volatile boolean connected = false;
    @Getter
    private volatile boolean loginSuccess = false;

    // ==================== 构造函数 ====================
    public SGIPClient(String host, int port, long sourceNodeId, long destNodeId,
                      String loginName, String loginPassword, String spId, String spNumber) {
        this.host = host;
        this.port = port;
        this.sourceNodeId = sourceNodeId;
        this.destNodeId = destNodeId;
        this.loginName = loginName;
        this.loginPassword = loginPassword;
        this.spId = spId;
        this.spNumber = spNumber;
        this.defaultServiceId = "000000000";
        initBootstrap();
    }

    // ==================== 初始化 Netty 管道 ====================
    private void initBootstrap() {
        workerGroup = new NioEventLoopGroup(1);
        bootstrap = new Bootstrap();
        final SGIPClient self = this;

        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new IdleStateHandler(30, 0, 0));
                        pipeline.addLast(new SGIPDecoder());
                        pipeline.addLast(new SGIPEncoder());
                        pipeline.addLast(new SGIPClientHandler(self));
                    }
                });
    }

    // ==================== 连接管理（无参，业务层调用） ====================
    /**
     * 主动建立连接（业务层调用入口）
     */
    public void connect() {
        if (connected && loginSuccess) {
            log.warn("SGIP 已连接且登录成功，无需重复连接");
            return;
        }
        try {
            ChannelFuture future = bootstrap.connect(host, port).sync();
            if (future.isSuccess()) {
                this.channel = future.channel();
                this.connected = true;
                log.info("SGIP 连接网关成功: {}:{}", host, port);
            } else {
                log.error("SGIP 连接网关失败: {}:{}", host, port, future.cause());
                throw new RuntimeException("连接网关失败", future.cause());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("连接被中断", e);
        }
    }

    /**
     * 主动断开连接
     */
    public void disconnect() {
        try {
            if (channel != null && channel.isActive()) {
                sendTerminate(channel);
                channel.closeFuture().sync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            connected = false;
            loginSuccess = false;
            if (workerGroup != null && !workerGroup.isShuttingDown()) {
                workerGroup.shutdownGracefully();
            }
            log.info("SGIP 连接已断开");
        }
    }

    // ==================== 核心协议方法（带 Channel 参数，给 Handler 调用） ====================
    /**
     * 发送登录请求（Handler 调用，传入 Channel）
     * 对应 Handler 中 channelActive 调用：client.sendBind(ctx.channel())
     */
    public void sendBind(Channel channel) {
        SGIPBind bind = new SGIPBind();
        bind.setHeader(buildHeader(SGIPCommand.SGIP_BIND));
        bind.setLoginType(1); // 1=SP接入SMG
        bind.setName(loginName);
        bind.setPassword(loginPassword);
        sendMessage(channel, bind);
        log.debug("发送登录请求");
    }

    /**
     * 发送心跳请求（Handler 调用，传入 Channel）
     * 对应 Handler 中 userEventTriggered 调用：client.sendActiveTest(ctx.channel())
     */
    public void sendActiveTest(Channel channel) {
        if (!loginSuccess) {
            return;
        }
        SGIPActiveTest activeTest = new SGIPActiveTest();
        activeTest.setHeader(buildHeader(SGIPCommand.SGIP_ACTIVE_TEST));
        sendMessage(channel, activeTest);
        log.debug("发送心跳请求");
    }

    /**
     * 发送断开请求
     */
    public void sendTerminate(Channel channel) {
        SGIPTerminate terminate = new SGIPTerminate();
        terminate.setHeader(buildHeader(SGIPCommand.SGIP_TERMINATE));
        sendMessage(channel, terminate);
        log.debug("发送断开请求");
    }

    // ==================== 消息发送通用方法 ====================
    /**
     * 指定通道发送消息（内部基础方法）
     */
    public void sendMessage(Channel channel, SGIPMessage msg) {
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("通道不可用，无法发送消息");
        }
        channel.writeAndFlush(msg).addListener(future -> {
            if (!future.isSuccess()) {
                log.error("消息发送失败, 命令: 0x{}",
                        Integer.toHexString(msg.getCommandId()), future.cause());
            }
        });
    }

    /**
     * 默认通道发送消息（业务层调用）
     */
    public void sendMessage(SGIPMessage msg) {
        sendMessage(this.channel, msg);
    }

    // ==================== 业务发送方法（无参，业务层调用） ====================
    /**
     * 提交单条短信（业务层简化调用）
     * @param destNumber 目标手机号
     * @param content 短信内容
     * @param msgFormat 编码格式：0=ASCII 8=UCS2 15=GBK
     * @return 消息流水号
     */
    public long submit(String destNumber, String content, int msgFormat) {
        SGIPSubmit submit = new SGIPSubmit();
        submit.setHeader(buildHeader(SGIPCommand.SGIP_SUBMIT));

        // 填充公共字段（严格按 SGIP 1.2 表 4-17 顺序）
        submit.setSpNumber(spNumber);
        submit.setChargeNumber(spNumber);
        submit.setUserCount(1);
        submit.setUserNumber(new String[]{destNumber});
        submit.setCorpId(spId);
        submit.setServiceType(defaultServiceId);
        submit.setFeeType(1);
        submit.setFeeValue("0");
        submit.setGivenValue("0");
        submit.setAgentFlag(0);
        // 非 MO 点播触发的 MT 下发
        submit.setMorelatetoMTFlag(2);
        submit.setPriority(0);
        submit.setExpireTime("");
        submit.setScheduleTime("");
        // 要求无论成功失败都回状态报告
        submit.setReportFlag(1);
        submit.setTpPid(0);
        // 单条普通短信，内容不含 UDH
        submit.setTpUdhi(0);
        submit.setMessageCoding(msgFormat);
        submit.setMessageType(0);

        // 编码内容
        byte[] contentBytes = encodeContent(content, msgFormat);
        submit.setMessageLength(contentBytes.length);
        submit.setMessageContent(contentBytes);

        sendMessage(submit);
        log.debug("提交短信: 目标={}, 长度={}", destNumber, contentBytes.length);
        return submit.getHeader().getSequenceNumber();
    }

    /**
     * 提交长短信（自动拆分）
     * <p>
     * 超过单条长度的内容会自动拆分为多条 Submit，每条携带 UDH 头，
     * 手机终端根据 UDH 自动重组为一条完整短信。
     * </p>
     *
     * @param destNumber 目标手机号
     * @param content    完整短信内容
     * @param msgFormat  编码格式：0=ASCII 8=UCS2 15=GBK
     * @return 第一片的消息流水号（其余分片流水号递增）
     */
    public long submitLongSms(String destNumber, String content, int msgFormat) throws IOException {
        return submitLongSms(destNumber, content, msgFormat, false);
    }

    /**
     * 提交长短信（自动拆分，可指定 16 位参考号）
     *
     * @param destNumber   目标手机号
     * @param content      完整短信内容
     * @param msgFormat    编码格式：0=ASCII 8=UCS2 15=GBK
     * @param use16bitRef  是否使用 16 位参考号（分片超过 255 片时必须为 true）
     * @return 第一片的消息流水号
     */
    public long submitLongSms(String destNumber, String content, int msgFormat, boolean use16bitRef)
            throws IOException {

        if (!loginSuccess) {
            throw new IllegalStateException("SGIP 未登录，无法发送短信");
        }

        // 1. 拆分长短信（只填充分片字段：msgFormat/msgContent/msgLength/morelatetoMsg）
        List<SGIPSubmit> pieces = SGIPLongSmsUtil.split(content, msgFormat, use16bitRef);

        log.info("长短信已拆分为 {} 片, 目标={}", pieces.size(), destNumber);

        long firstSeq = -1;

        // 2. 逐条填充业务字段并发送
        for (int i = 0; i < pieces.size(); i++) {
            SGIPSubmit piece = pieces.get(i);
            piece.setHeader(buildHeader(SGIPCommand.SGIP_SUBMIT));

            // 填充公共业务字段（拆分工具已设置 messageCoding/messageContent/messageLength/tpUdhi）
            piece.setSpNumber(spNumber);
            piece.setChargeNumber(spNumber);
            piece.setUserCount(1);
            piece.setUserNumber(new String[]{destNumber});
            piece.setCorpId(spId);
            piece.setServiceType(defaultServiceId);
            piece.setFeeType(1);
            piece.setFeeValue("0");
            piece.setGivenValue("0");
            piece.setAgentFlag(0);
            piece.setMorelatetoMTFlag(2);
            piece.setPriority(0);
            piece.setExpireTime("");
            piece.setScheduleTime("");
            piece.setReportFlag(1);
            piece.setTpPid(0);
            piece.setMessageType(0);

            if (i == 0) {
                firstSeq = piece.getHeader().getSequenceNumber();
            }

            sendMessage(piece);
            log.debug("长短信分片 {}/{} 已发送, seq={}, 长度={}",
                    i + 1, pieces.size(), piece.getHeader().getSequenceNumber(), piece.getMessageLength());
        }

        return firstSeq;
    }

    // ==================== 内部工具方法 ====================
    /**
     * 构建消息头，自动生成序列号
     */
    private SGIPHeader buildHeader(int commandId) {
        SGIPHeader header = new SGIPHeader();
        header.setCommandId(commandId);
        header.setSourceNodeId(sourceNodeId);
        header.setDestNodeId(destNodeId);
        header.setSequenceNumber(sequenceGenerator.getAndIncrement() & 0xFFFFFFFFL);
        return header;
    }

    /**
     * 按编码格式编码短信内容
     */
    private byte[] encodeContent(String content, int msgFormat) {
        // Java 8 兼容：不使用 switch 表达式（Java 14 语法）
        switch (msgFormat) {
            case 0:
                return content.getBytes(StandardCharsets.US_ASCII);
            case 8:
                return content.getBytes(StandardCharsets.UTF_16BE);
            case 15:
                return content.getBytes(java.nio.charset.Charset.forName("GBK"));
            default:
                throw new IllegalArgumentException("不支持的编码格式: " + msgFormat);
        }
    }

    // ==================== 业务回调方法（与 Handler 调用完全对应） ====================
    /**
     * 登录成功回调
     * 对应 Handler 调用：client.onLoginSuccess(ctx.channel())
     */
    public void onLoginSuccess(Channel channel) {
        this.loginSuccess = true;
        this.channel = channel;
        log.info("SGIP 登录成功");
    }

    /**
     * 连接断开回调
     * 对应 Handler 调用：client.onDisconnected()
     */
    public void onDisconnected() {
        this.loginSuccess = false;
        this.connected = false;
        log.warn("SGIP 连接断开");
    }

    /**
     * 提交响应回调
     * 对应 Handler 调用：client.onSubmitResp(resp)
     */
    public void onSubmitResp(SGIPSubmitResp resp) {
        log.debug("收到提交响应: result={}", resp.getResult());
    }

    /**
     * 上行短信回调
     * 对应 Handler 调用：client.onDeliver(deliver)
     */
    public void onDeliver(SGIPDeliver deliver) {
        log.debug("收到上行短信: 来自={}", deliver.getSrcTerminalId());
    }

    /**
     * 状态报告回调
     * 对应 Handler 调用：client.onReport(report)
     */
    public void onReport(SGIPReport report) {
        log.debug("收到状态报告: 号码={}, 状态={}", report.getUserNumber(), report.getState());
    }
}
