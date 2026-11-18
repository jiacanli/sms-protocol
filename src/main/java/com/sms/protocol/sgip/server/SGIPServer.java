package com.sms.protocol.sgip.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * SGIP 服务端
 * <p>
 * SP 端作为服务端监听端口，接收 SMG 的主动连接，用于接收：
 * <ul>
 *   <li>Deliver - 上行短信（MO）</li>
 *   <li>Report - 状态报告（状态回执）</li>
 * </ul>
 * 与 {@link com.sms.protocol.sgip.client.SGIPClient} 配合使用：
 * Client 负责连接 SMG 发送 Submit，Server 负责监听端口接收推送。
 * </p>
 *
 * <pre>
 * // 使用示例
 * SGIPServerConfig config = new SGIPServerConfig(8890, 3030123456L, "spname", "sppwd");
 * SGIPServer server = new SGIPServer(config, new SGIPServerListener() {
 *     public void onDeliver(SGIPDeliver deliver) { ... }
 *     public void onReport(SGIPReport report) { ... }
 * });
 * server.start();
 * // ...
 * server.stop();
 * </pre>
 */
@Slf4j
@Data
public class SGIPServer {

    private final SGIPServerConfig config;
    private final SGIPServerListener listener;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Getter
    private volatile boolean running = false;

    public SGIPServer(SGIPServerConfig config, SGIPServerListener listener) {
        this.config = config;
        this.listener = listener;
    }

    /**
     * 启动服务端，绑定监听端口
     */
    public void start() throws InterruptedException {
        if (running) {
            log.warn("SGIP 服务端已在运行，无需重复启动");
            return;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis())
                .childHandler(new SGIPServerInitializer(config, listener));

        ChannelFuture future = bootstrap.bind(config.getPort()).sync();
        if (future.isSuccess()) {
            serverChannel = future.channel();
            running = true;
            log.info("SGIP 服务端启动成功，监听端口: {}, 节点号: {}",
                    config.getPort(), config.getSourceNodeId());
        } else {
            log.error("SGIP 服务端启动失败，端口: {}", config.getPort(), future.cause());
            stop();
            throw new RuntimeException("SGIP 服务端绑定端口失败: " + config.getPort(), future.cause());
        }
    }

    /**
     * 同步等待服务端通道关闭（阻塞调用）
     */
    public void waitUntilShutdown() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    /**
     * 停止服务端，释放资源
     */
    public void stop() {
        running = false;

        if (serverChannel != null && serverChannel.isActive()) {
            try {
                serverChannel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (bossGroup != null && !bossGroup.isShuttingDown()) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null && !workerGroup.isShuttingDown()) {
            workerGroup.shutdownGracefully();
        }

        log.info("SGIP 服务端已停止");
    }
}
