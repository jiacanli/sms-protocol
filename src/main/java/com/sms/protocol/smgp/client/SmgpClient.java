package com.sms.protocol.smgp.client;

import com.sms.protocol.smgp.message.*;
import com.sms.protocol.utils.SmgpLongSmsUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SMGP 客户端（中国电信短消息网关协议，可配置）。
 * <p>
 * 能力：连接管理（含断线自动重连）、登录与等待、读空闲心跳（ActiveTest）、
 * sequenceId → 响应关联（CompletableFuture）、普通/长短信自动拆分发送、Exit 注销。
 * <p>
 * 典型用法：
 * <pre>{@code
 * SmgpClientConfig cfg = new SmgpClientConfig();
 * cfg.setHost("gw.example.com");
 * cfg.setPort(8890);
 * cfg.setClientId("17101197");
 * cfg.setSecret("112233");
 * cfg.setSrcId("10691234");
 *
 * SmgpClient client = new SmgpClient(cfg);
 * client.start();
 * if (client.awaitLogin(10, TimeUnit.SECONDS)) {
 *     List<CompletableFuture<SmgpSubmitResp>> futures =
 *             client.sendSms("13800138000", "你好，SMGP 长短信测试……", SmgpConstants.MSG_FORMAT_UCS2);
 * }
 * client.stop();
 * }</pre>
 */
@Slf4j
public class SmgpClient {

    private final SmgpClientConfig config;
    private final SmgpMessageListener listener;

    private EventLoopGroup group;
    private volatile Channel channel;
    private volatile boolean running = false;

    /** 是否已登录 */
    private volatile boolean loggedIn = false;

    /** 登录结果（Handler 回调 complete） */
    private final CompletableFuture<SmgpLoginResp> loginFuture = new CompletableFuture<>();

    /** sequenceId -> 待响应（SubmitResp 等） */
    private final ConcurrentHashMap<Integer, CompletableFuture<SmgpMessage>> pending = new ConcurrentHashMap<>();

    public SmgpClient(SmgpClientConfig config) {
        this(config, new SmgpMessageListener() {});
    }

    public SmgpClient(SmgpClientConfig config, SmgpMessageListener listener) {
        this.config = config;
        this.listener = listener;
    }

    // ==================================================================
    //                       连接 / 断开 / 重连
    // ==================================================================

    /**
     * 启动并建立连接（连接建立后自动发送 Login）
     */
    public void start() throws InterruptedException {
        running = true;
        group = new NioEventLoopGroup(1);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout())
                .handler(new SmgpClientInitializer(config, this));

        ChannelFuture future = bootstrap.connect(config.getHost(), config.getPort()).sync();
        this.channel = future.channel();
        log.info("SMGP 客户端已连接 {}:{}", config.getHost(), config.getPort());

        // 连接关闭时触发重连
        future.channel().closeFuture().addListener((ChannelFutureListener) f -> {
            if (running && config.isReconnectEnabled()) {
                log.warn("连接关闭，{} 秒后重连...", config.getReconnectIntervalSeconds());
                f.channel().eventLoop().schedule(this::reconnect,
                        config.getReconnectIntervalSeconds(), TimeUnit.SECONDS);
            }
        });
    }

    private void reconnect() {
        if (!running) return;
        try {
            start();
        } catch (Exception e) {
            log.error("重连失败，{} 秒后重试", config.getReconnectIntervalSeconds(), e);
            if (group != null) {
                group.next().schedule(this::reconnect,
                        config.getReconnectIntervalSeconds(), TimeUnit.SECONDS);
            }
        }
    }

    /**
     * 停止：注销并关闭连接，同时禁止重连
     */
    public void stop() {
        running = false;
        loggedIn = false;
        Channel ch = channel;
        if (ch != null && ch.isActive()) {
            try {
                sendExit(ch);
            } catch (Exception e) {
                log.warn("发送 Exit 失败", e);
            }
            ch.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        completeAllPending(new ClosedChannelException());
    }

    // ==================================================================
    //                       登录
    // ==================================================================

    /**
     * 发送 Login（由 Handler 在连接建立后自动调用；也可手动触发）
     */
    public void sendLogin(Channel ch) {
        if (!ch.isActive()) return;
        SmgpLogin login = SmgpLogin.build(config.getClientId(), config.getSecret(), config.getLoginMode());
        ch.writeAndFlush(login);
        log.info("已发送 SMGP Login, clientId={}, loginMode={}", config.getClientId(), config.getLoginMode());
    }

    /**
     * 等待登录结果
     *
     * @return true=登录成功；false=超时或登录失败
     */
    public boolean awaitLogin(long timeout, TimeUnit unit) {
        try {
            SmgpLoginResp resp = loginFuture.get(timeout, unit);
            return resp != null && resp.getStatus() == 0;
        } catch (Exception e) {
            log.warn("等待登录超时或中断: {}", e.toString());
            return false;
        }
    }

    /**
     * 返回登录结果 future（status==0 表示成功）
     */
    public CompletableFuture<SmgpLoginResp> loginFuture() {
        return loginFuture;
    }

    void onLoginSuccess(SmgpLoginResp resp) {
        loggedIn = true;
        loginFuture.complete(resp);
        listener.onLoginSuccess(this);
    }

    void onLoginFailed(SmgpLoginResp resp) {
        loggedIn = false;
        loginFuture.complete(resp);
        listener.onLoginFailed(resp);
    }

    // ==================================================================
    //                       心跳 / 注销
    // ==================================================================

    /**
     * 发送 ActiveTest 心跳（读空闲时由 Handler 自动触发）
     */
    public void sendActiveTest(Channel ch) {
        if (ch == null || !ch.isActive()) return;
        SmgpActiveTest at = new SmgpActiveTest();
        ch.writeAndFlush(at);
    }

    /**
     * 发送 Exit 注销请求
     */
    public void sendExit() {
        Channel ch = channel;
        if (ch != null && ch.isActive()) {
            sendExit(ch);
        }
    }

    private void sendExit(Channel ch) {
        SmgpExit exit = new SmgpExit();
        ch.writeAndFlush(exit);
        log.info("已发送 SMGP Exit");
    }

    // ==================================================================
    //                       发送短信（核心）
    // ==================================================================

    /**
     * 发送单条 Submit 并等待 SubmitResp（内部自动关联 sequenceId）。
     * 注意：submit 的 header.sequenceId 应保持唯一；重复发送同一实例会复用同一 seq，
     * 此时请调用方自行更换 sequenceId 或使用便捷 sendSms。
     */
    public CompletableFuture<SmgpSubmitResp> sendSubmitAsync(SmgpSubmit submit) {
        return sendSubmitAsync(submit, config.getSubmitTimeoutMillis());
    }

    public CompletableFuture<SmgpSubmitResp> sendSubmitAsync(SmgpSubmit submit, long timeoutMillis) {
        if (!loggedIn || channel == null || !channel.isActive()) {
            CompletableFuture<SmgpSubmitResp> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException("SMGP 未登录或连接不可用"));
            return f;
        }
        int seq = submit.getHeader().getSequenceId();
        CompletableFuture<SmgpMessage> future = registerPending(seq, timeoutMillis);
        channel.writeAndFlush(submit);
        return future.thenApply(m -> (SmgpSubmitResp) m);
    }

    /**
     * 同步发送单条 Submit
     */
    public SmgpSubmitResp sendSubmit(SmgpSubmit submit) {
        return sendSubmit(submit, config.getSubmitTimeoutMillis());
    }

    public SmgpSubmitResp sendSubmit(SmgpSubmit submit, long timeoutMillis) {
        try {
            return sendSubmitAsync(submit, timeoutMillis).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("等待 SubmitResp 失败", e);
        }
    }

    /**
     * 发送普通/长短信（自动拆分），返回每条分片的 SubmitResp future。
     *
     * @param destNumber 接收号码
     * @param content    短文明文
     * @param msgFmt     编码：0=ASCII / 8=UCS2（中文推荐）/ 15=GBK
     */
    public List<CompletableFuture<SmgpSubmitResp>> sendSms(String destNumber,
                                                           String content,
                                                           int msgFmt) {
        return sendSms(config.getSrcId(), destNumber, config.getServiceId(), content, msgFmt);
    }

    /**
     * 发送普通/长短信（自动拆分）
     *
     * @param srcId       接入号（21 字节）
     * @param destNumber  接收号码
     * @param serviceId   业务代码（10 字节）
     * @param content     短文明文
     * @param msgFmt      编码：0=ASCII / 8=UCS2 / 15=GBK
     */
    public List<CompletableFuture<SmgpSubmitResp>> sendSms(String srcId,
                                                           String destNumber,
                                                           String serviceId,
                                                           String content,
                                                           int msgFmt) {
        if (!loggedIn || channel == null || !channel.isActive()) {
            throw new IllegalStateException("SMGP 未登录或连接不可用");
        }
        if (destNumber == null || destNumber.isEmpty()) {
            throw new IllegalArgumentException("destNumber 不能为空");
        }

        // 1. 拆分（只传 String，分片字段由工具负责）
        List<SmgpSubmit> pieces = SmgpLongSmsUtil.split(content, msgFmt);
        log.info("内容已拆分为 {} 条分片", pieces.size());

        List<CompletableFuture<SmgpSubmitResp>> futures = new ArrayList<>(pieces.size());
        for (SmgpSubmit piece : pieces) {
            fillSubmit(piece, srcId, destNumber, serviceId);
            futures.add(sendSubmitAsync(piece));
        }
        return futures;
    }

    /**
     * 群发：每个号码独立生成一组长短信分片（参考号各自独立）
     */
    public List<CompletableFuture<SmgpSubmitResp>> sendSmsMultiDest(String srcId,
                                                                    List<String> destNumbers,
                                                                    String serviceId,
                                                                    String content,
                                                                    int msgFmt) {
        if (!loggedIn || channel == null || !channel.isActive()) {
            throw new IllegalStateException("SMGP 未登录或连接不可用");
        }
        List<CompletableFuture<SmgpSubmitResp>> futures = new ArrayList<>();
        for (String dest : destNumbers) {
            List<SmgpSubmit> pieces = SmgpLongSmsUtil.split(content, msgFmt);
            for (SmgpSubmit piece : pieces) {
                fillSubmit(piece, srcId, dest, serviceId);
                futures.add(sendSubmitAsync(piece));
            }
        }
        return futures;
    }

    /**
     * 填充 SmgpSubmit 的业务字段（分片字段由 SmgpLongSmsUtil 负责，此处不覆盖）
     */
    private void fillSubmit(SmgpSubmit submit, String srcId, String destNumber, String serviceId) {
        submit.setNeedReport((byte) 1);
        submit.setPriority((byte) 0);
        submit.setServiceId(serviceId);
        submit.setFeeType("01");
        submit.setFeeCode("000000");
        submit.setFixedFee("000000");
        submit.setValidTime("");
        submit.setAtTime("");
        submit.setSrcTermId(srcId);
        submit.setChargeTermId(srcId);
        submit.setDestTermIdCount((byte) 1);
        submit.setDestTermIds(Collections.singletonList(destNumber));
        submit.setReserve("");
    }

    // ==================================================================
    //                    sequenceId → 响应关联
    // ==================================================================

    private CompletableFuture<SmgpMessage> registerPending(int seq, long timeoutMillis) {
        CompletableFuture<SmgpMessage> future = new CompletableFuture<>();
        pending.put(seq, future);
        // 超时清理，避免 pending 无限膨胀
        if (timeoutMillis > 0) {
            // Java 8 兼容：CompletableFuture.orTimeout 为 Java 9 API，改用 Netty EventLoop 定时器
            EventLoopGroup g = this.group;
            if (g != null) {
                g.next().schedule(() -> {
                    if (future.completeExceptionally(
                            new TimeoutException("SMGP response timeout, seq=" + seq))) {
                        pending.remove(seq, future);
                    }
                }, timeoutMillis, TimeUnit.MILLISECONDS);
            }
        }
        return future;
    }

    private void completePending(int seq, SmgpMessage resp) {
        CompletableFuture<SmgpMessage> future = pending.remove(seq);
        if (future != null) {
            future.complete(resp);
        } else {
            log.warn("收到未注册的响应 seq={}, cmd=0x{}",
                    seq, Integer.toHexString(resp.getHeader().getCommandId()));
        }
    }

    private void completeAllPending(Throwable cause) {
        for (Integer seq : pending.keySet()) {
            CompletableFuture<SmgpMessage> future = pending.remove(seq);
            if (future != null) {
                future.completeExceptionally(cause);
            }
        }
    }

    // ==================================================================
    //               Handler 回调（package-private）
    // ==================================================================

    void onSubmitResp(SmgpSubmitResp resp) {
        completePending(resp.getHeader().getSequenceId(), resp);
        listener.onSubmitResp(resp);
    }

    void onDeliver(SmgpDeliver deliver) {
        listener.onDeliver(deliver);
    }

    void onReport(SmgpReport report, SmgpDeliver deliver) {
        listener.onReport(report, deliver);
    }

    void onActiveTestResp(SmgpActiveTestResp resp) {
        listener.onActiveTestResp(resp);
    }

    void onExitResp(SmgpExitResp resp) {
        listener.onExitResp(resp);
    }

    void onExitRequested() {
        listener.onExitRequested(this);
    }

    void onException(Throwable cause) {
        listener.onException(cause);
    }

    void onDisconnected(Channel ch) {
        loggedIn = false;
        completeAllPending(new ClosedChannelException());
        listener.onChannelClosed(this, null);
    }

    // ==================================================================
    //                        状态访问
    // ==================================================================

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean isRunning() {
        return running;
    }

    public SmgpClientConfig getConfig() {
        return config;
    }

    public Channel getChannel() {
        return channel;
    }
}
