package com.sms.protocol.cmpp20.client;



import com.sms.protocol.cmpp20.codec.CMPPDecoder;
import com.sms.protocol.cmpp20.codec.CMPPEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class CMPPClientInitializer extends ChannelInitializer<SocketChannel> {

    private final CMPPClientConfig config;
    private final CMPPClient client;

    public CMPPClientInitializer(CMPPClientConfig config, CMPPClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                // 读空闲超过阈值触发 USER_EVENT，用来发心跳
                .addLast("idle", new IdleStateHandler(
                        config.getReaderIdleSeconds(), 0, 0, TimeUnit.SECONDS))
                // CMPP 解码器（假设你已实现）
                .addLast("decoder", new CMPPDecoder())
                // CMPP 编码器（假设你已实现）
                .addLast("encoder", new CMPPEncoder())
                // 业务处理器
                .addLast("handler", new CMPPClientHandler(client));
    }
}