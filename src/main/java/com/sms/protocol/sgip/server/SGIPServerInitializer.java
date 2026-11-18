package com.sms.protocol.sgip.server;

import com.sms.protocol.sgip.codec.SGIPDecoder;
import com.sms.protocol.sgip.codec.SGIPEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * SGIP 服务端管道初始化
 * 复用与客户端相同的编解码器（SGIPDecoder / SGIPEncoder 是协议无关的）
 */
public class SGIPServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SGIPServerConfig config;
    private final SGIPServerListener listener;

    public SGIPServerInitializer(SGIPServerConfig config, SGIPServerListener listener) {
        this.config = config;
        this.listener = listener;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                // 空闲检测：读空闲超时关闭连接，写空闲主动发心跳（如配置）
                .addLast("idle", new IdleStateHandler(
                        config.getReaderIdleSeconds(),
                        config.getWriterIdleSeconds(),
                        0,
                        TimeUnit.SECONDS))
                // 解码器（粘包拆包 + 消息体反序列化）
                .addLast("decoder", new SGIPDecoder())
                // 编码器
                .addLast("encoder", new SGIPEncoder())
                // 服务端业务处理器
                .addLast("handler", new SGIPServerHandler(config, listener));
    }
}
