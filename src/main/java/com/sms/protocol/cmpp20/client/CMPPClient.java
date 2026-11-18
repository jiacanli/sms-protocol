package com.sms.protocol.cmpp20.client;


import com.sms.protocol.cmpp20.message.*;
import com.sms.protocol.utils.CMPPLongSmsUtil;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CMPPClient {

    private final CMPPClientConfig config;

    private EventLoopGroup group;
    private volatile Channel channel;

    /** 是否已登录 */
    @Getter
    private volatile boolean loggedIn = false;

    /** 是否自动重连 */
    private volatile boolean autoReconnect = true;

    public CMPPClient(CMPPClientConfig config) {
        this.config = config;
    }

    // ==================================================================
    //                       连接与断开
    // ==================================================================

    public void start() throws InterruptedException {
        group = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout())
                .handler(new CMPPClientInitializer(config, this));

        ChannelFuture future = bootstrap.connect(config.getHost(), config.getPort()).sync();
        this.channel = future.channel();
        log.info("CMPP 客户端已连接 {}:{}", config.getHost(), config.getPort());

        // 连接关闭时触发重连
        future.channel().closeFuture().addListener((ChannelFutureListener) f -> {
            if (autoReconnect) {
                log.warn("连接关闭，5 秒后重连...");
                f.channel().eventLoop().schedule(this::reconnect,
                        5, TimeUnit.SECONDS);
            }
        });
    }

    private void reconnect() {
        if (!autoReconnect) return;
        try {
            start();
        } catch (Exception e) {
            log.error("重连失败，5 秒后重试", e);
            channel.eventLoop().schedule(this::reconnect, 5, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        autoReconnect = false;
        if (channel != null && channel.isActive()) {
            sendTerminate(channel);
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    // ==================================================================
    //                       登录 / 心跳 / 断开
    // ==================================================================

    void sendConnect(Channel ch) {
        CMPPConnect connect = new CMPPConnect();
        CMPPHeader header = new CMPPHeader();
        header.setCommandId(CMPPCommand.CMPP_CONNECT);
        header.setSequenceId(SequenceIdGenerator.next());
        connect.setHeader(header);

        connect.build(config.getSourceAddr(), config.getSharedSecret()); // 4 字节
        // authenticator 由 encoder 或这里按 MD5 算法计算，你的 encoder 若已处理可忽略

        ch.writeAndFlush(connect);
        log.info("已发送 CMPP_CONNECT");
    }

    void onLoginSuccess(Channel ch) {
        this.loggedIn = true;
        // 登录成功后可在此做业务初始化，例如发送积压消息
    }

    void sendActiveTest(Channel ch) {
        if (!ch.isActive()) return;
        CMPPActiveTest active = new CMPPActiveTest();
        CMPPHeader header = new CMPPHeader();
        header.setCommandId(CMPPCommand.CMPP_ACTIVE_TEST);
        header.setSequenceId(SequenceIdGenerator.next());
        active.setHeader(header);
        ch.writeAndFlush(active);
    }

    private void sendTerminate(Channel ch) {
        // 断开时发送 CMPP_TERMINATE 的实现当前为空：如需协议完整可在此补充
    }

    void onDisconnected() {
        this.loggedIn = false;
    }

    void onSubmitResp(CMPPSubmitResp resp) {
        // 可在此统计发送成功率、记录日志等
    }

    // ==================================================================
    //                       发送短信（核心）
    // ==================================================================

    /**
     * 发送普通/长短信（自动拆分）
     *
     * @param srcId       源地址（21 字节）
     * @param destNumber  目标号码
     * @param serviceId   业务类型
     * @param content     短信明文
     * @param msgFmt      编码：0/8/15，推荐 UCS2=8
     */
    public void sendSms(String srcId,
                        String destNumber,
                        String serviceId,
                        String content,
                        int msgFmt) throws IOException {

        if (!loggedIn || channel == null || !channel.isActive()) {
            throw new IllegalStateException("CMPP 未登录或连接不可用");
        }

        // 1. 拆分（只传 String）
        List<CMPPSubmit> pieces = CMPPLongSmsUtil.split(content, msgFmt);

        log.info("内容已拆分为 {} 条分片", pieces.size());

        // 2. 逐条填充业务字段并写入 channel
        for (int i = 0; i < pieces.size(); i++) {
            CMPPSubmit piece = pieces.get(i);
            fillSubmit(piece, srcId, destNumber, serviceId);

            // 顺序发送，最后一条 flush（也可每条都 flush）
            ChannelFuture future = channel.write(piece);


            future.addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    log.error("分片发送失败: pkNumber={}, seq={}",
                            piece.getPkNumber(), piece.getSequenceId(), f.cause());
                } else {
                    log.debug("分片发送成功: pkNumber={}/{}, seq={}",
                            piece.getPkNumber(), piece.getPkTotal(), piece.getSequenceId());
                }
            });
        }

        // 统一 flush
        channel.flush();
    }

    /**
     * 填充 CMPPSubmit 的业务字段（分片字段由工具类负责）
     */
    private void fillSubmit(CMPPSubmit submit,
                            String srcId,
                            String destNumber,
                            String serviceId) {
        CMPPHeader header = new CMPPHeader();
        header.setCommandId(CMPPCommand.CMPP_SUBMIT);
        header.setSequenceId(SequenceIdGenerator.next());
        submit.setHeader(header);

        submit.setMsgId(0L);
        submit.setRegisteredDelivery(1);
        submit.setMsgLevel(1);
        submit.setServiceId(serviceId);
        submit.setFeeUserType(2);
        submit.setFeeTerminalId("");
        submit.setTpPid(0);
        submit.setMsgSrc(config.getSourceAddr());
        submit.setFeeType("01");
        submit.setFeeCode("000000");
        submit.setValIdTime("");
        submit.setAtTime("");
        submit.setSrcId(srcId);
        submit.setDestTerminalId(new String[]{destNumber});
        submit.setDestUsrTl(1);
    }

    // ==================================================================
    //                       群发（每号码独立参考号）
    // ==================================================================

    /**
     * 群发长短信：每个号码生成独立的一组长短信分片
     */
    public void sendSmsMultiDest(String srcId,
                                 List<String> destNumbers,
                                 String serviceId,
                                 String content,
                                 int msgFmt) throws IOException {
        if (!loggedIn || channel == null || !channel.isActive()) {
            throw new IllegalStateException("CMPP 未登录或连接不可用");
        }

        for (String dest : destNumbers) {
            List<CMPPSubmit> pieces = CMPPLongSmsUtil.split(content, msgFmt);
            for (CMPPSubmit piece : pieces) {
                fillSubmit(piece, srcId, dest, serviceId);
                channel.write(piece);
            }
        }
        channel.flush();
    }

}