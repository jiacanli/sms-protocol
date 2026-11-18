package com.sms.protocol.smgp.codec;


import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.smgp.SmgpMessageFactory;
import com.sms.protocol.smgp.message.SmgpMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SmgpDecoder extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(SmgpDecoder.class);

    /** 单个 SMGP 数据包的最大长度（防呆：消息内容上限 160 * 群发数 + 固定部分，留出余量） */
    private static final int MAX_PACKET_LENGTH = 64 * 1024;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < SmgpConstants.HEADER_LENGTH) {
            return;
        }

        in.markReaderIndex();
        int totalLength = in.readInt();

        // 包长合法性校验：至少等于消息头长度，且不超过上限
        if (totalLength < SmgpConstants.HEADER_LENGTH || totalLength > MAX_PACKET_LENGTH) {
            logger.warn("Invalid SMGP packet length: {}, closing connection", totalLength);
            ctx.close();
            return;
        }

        if (in.readableBytes() < totalLength - 4) {
            in.resetReaderIndex();
            return;
        }

        int commandId = in.readInt();
        int sequenceId = in.readInt();

        // in.readBytes() 返回 slice，与累积缓冲共享引用计数：解析完必须 release，
        // 否则累积缓冲（pooled direct）在连接关闭时引用计数无法归零，内存不归还
        ByteBuf bodyBuf = in.readBytes(totalLength - SmgpConstants.HEADER_LENGTH);

        try {
            SmgpMessage msg = SmgpMessageFactory.create(commandId);
            msg.getHeader().setPacketLength(totalLength);
            msg.getHeader().setCommandId(commandId);
            msg.getHeader().setSequenceId(sequenceId);
            msg.decodeBody(bodyBuf);
            out.add(msg);
        } catch (Exception e) {
            // 未知命令字或包体解析异常：记录日志并丢弃该包，避免整个连接中断
            logger.warn("Failed to decode SMGP packet, commandId={}, seq={}: {}",
                    String.format("0x%08X", commandId), sequenceId, e.getMessage());
        } finally {
            bodyBuf.release();
        }
    }
}
