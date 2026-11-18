package com.sms.protocol.cmpp20.codec;

import com.sms.protocol.cmpp20.codec.mina.CMPPCodecKit;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class CMPPDecoder extends ByteToMessageDecoder {

    private static final int MAX_PACKET_LENGTH = 64 * 1024;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 1. 判断是否有足够字节读取总长度
        if (in.readableBytes() < 12) { // 头部固定12字节
            return;
        }

        // 标记读指针，以便判断包完整性
        in.markReaderIndex();
        int totalLength = in.readInt();
        int commandId = in.readInt();
        int sequenceId = in.readInt();

        // 包长合法性校验（防呆：非法包长直接关闭连接）
        if (totalLength < 12 || totalLength > MAX_PACKET_LENGTH) {
            log.warn("Invalid CMPP packet length: {}, closing connection", totalLength);
            ctx.close();
            return;
        }

        // 2. 判断消息体是否接收完整
        int bodyLength = totalLength - 12;
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex(); // 重置指针，等待后续数据
            return;
        }

        // 3. 解析消息体（拆包完成，body 解析复用 CMPPCodecKit，与 Mina 侧共用一份逻辑）
        //    in.readBytes() 返回 slice，与累积缓冲共享引用计数，解析完必须 release
        ByteBuf bodyBuf = in.readBytes(bodyLength);
        try {
            out.add(CMPPCodecKit.parseFrame(commandId, sequenceId, totalLength, bodyBuf));
        } catch (Exception e) {
            // 未知命令字或包体解析异常：丢弃该包，避免整个连接中断
            log.warn("Failed to decode CMPP packet, commandId={}, seq={}: {}",
                    String.format("0x%08X", commandId), sequenceId, e.getMessage());
        } finally {
            bodyBuf.release();
        }
    }
}
