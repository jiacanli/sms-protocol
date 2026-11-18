package com.sms.protocol.smgp.codec;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.smgp.message.SmgpMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class SmgpEncoder extends MessageToByteEncoder<SmgpMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, SmgpMessage msg, ByteBuf out) {
        // 使用 Unpooled 堆缓冲：由 GC 回收，避免 ctx.alloc()（pooled）必须手动 release 的泄漏风险
        ByteBuf bodyBuf = Unpooled.buffer();
        msg.encodeBody(bodyBuf);

        int totalLength = SmgpConstants.HEADER_LENGTH + bodyBuf.readableBytes();
        out.writeInt(totalLength);
        out.writeInt(msg.getHeader().getCommandId());
        out.writeInt(msg.getHeader().getSequenceId());
        out.writeBytes(bodyBuf);
    }
}

