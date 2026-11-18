package com.sms.protocol.sgip.codec;

import com.sms.protocol.sgip.codec.mina.SGIPCodecKit;
import com.sms.protocol.sgip.message.SGIPHeader;
import com.sms.protocol.utils.SGIPCodecUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * SGIP 协议解码器
 * 基于长度字段的粘包拆包 + 消息体反序列化（消息体解析复用 {@link SGIPCodecKit}，与 Mina 侧共用一份逻辑）
 */
public class SGIPDecoder extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(SGIPDecoder.class);

    /** 消息头固定长度：20字节 */
    private static final int HEADER_LENGTH = 20;

    /** 单个 SGIP 数据包的最大长度（防呆） */
    private static final int MAX_PACKET_LENGTH = 64 * 1024;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 可读字节不足消息头，等待
        if (in.readableBytes() < HEADER_LENGTH) {
            return;
        }

        // 标记当前读指针，用于半包回退
        in.markReaderIndex();

        // 1. 读取总长度
        int totalLength = in.readInt();

        // 包长合法性校验（防呆：非法包长直接关闭连接）
        if (totalLength < HEADER_LENGTH || totalLength > MAX_PACKET_LENGTH) {
            logger.warn("Invalid SGIP packet length: {}, closing connection", totalLength);
            ctx.close();
            return;
        }

        // 数据不足一帧，重置指针等待
        if (in.readableBytes() < totalLength - 4) {
            in.resetReaderIndex();
            return;
        }

        // 2. 解析消息头
        SGIPHeader header = new SGIPHeader();
        header.setTotalLength(totalLength);
        header.setCommandId(in.readInt());
        long[] seq = SGIPCodecUtil.readSequenceId(in);
        header.setSourceNodeId(seq[0]);
        header.setDestNodeId(seq[1]);
        header.setSequenceNumber(seq[2]);

        // 3. 解析消息体（拆包完成，复用 SGIPCodecKit）
        //    in.readBytes() 返回 slice，与累积缓冲共享引用计数，解析完必须 release
        ByteBuf bodyBuf = in.readBytes(totalLength - HEADER_LENGTH);
        try {
            out.add(SGIPCodecKit.parseFrame(header, bodyBuf));
        } catch (Exception e) {
            // 未知命令字或包体解析异常：丢弃该包，避免整个连接中断
            logger.warn("Failed to decode SGIP packet, commandId={}, seq={}: {}",
                    String.format("0x%08X", header.getCommandId()), header.getSequenceNumber(), e.getMessage());
        } finally {
            bodyBuf.release();
        }
    }
}
