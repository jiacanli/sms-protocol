package com.sms.protocol.sgip.codec;



import com.sms.protocol.sgip.message.SGIPHeader;
import com.sms.protocol.sgip.message.SGIPMessage;
import com.sms.protocol.utils.SGIPCodecUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * SGIP 协议编码器
 * 将消息对象序列化为字节流
 */
public class SGIPEncoder extends MessageToByteEncoder<SGIPMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, SGIPMessage msg, ByteBuf out) {
        SGIPHeader header = msg.getHeader();

        // 1. 预留消息头位置（20字节）
        int headerIndex = out.writerIndex();
        out.writeZero(20);

        // 2. 写入消息体
        msg.writeBody(out);

        // 3. 计算总长度，回填消息头
        int totalLength = out.writerIndex() - headerIndex;
        header.setTotalLength(totalLength);

        // 4. 写入完整消息头

        out.setInt(headerIndex, totalLength);                       // int → 4字节
        out.setInt(headerIndex + 4, header.getCommandId());         // int → 4字节
        out.writerIndex(headerIndex + 8);
        SGIPCodecUtil.writeSequenceId(out,
                header.getSourceNodeId(),
                header.getDestNodeId(),
                header.getSequenceNumber());

        out.writerIndex(headerIndex + totalLength);
    }


}

