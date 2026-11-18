package com.sms.protocol.smgp.client;

import com.sms.protocol.smgp.codec.SmgpDecoder;
import com.sms.protocol.smgp.codec.SmgpEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * SMGP 客户端管道装配：读空闲心跳 + 编解码 + 业务处理器。
 */
public class SmgpClientInitializer extends ChannelInitializer<SocketChannel> {

    private final SmgpClientConfig config;
    private final SmgpClient client;

    public SmgpClientInitializer(SmgpClientConfig config, SmgpClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        // 读空闲超过阈值触发 USER_EVENT，用于发送 ActiveTest 心跳
        p.addLast("idle", new IdleStateHandler(
                config.getReaderIdleSeconds(), 0, 0, TimeUnit.SECONDS));
        p.addLast("decoder", new SmgpDecoder());
        p.addLast("encoder", new SmgpEncoder());
        p.addLast("handler", new SmgpClientHandler(config, client));
    }
}
