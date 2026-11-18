package com.sms.protocol.cmpp20.codec;

import com.sms.protocol.cmpp20.message.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class CMPPEncoder extends MessageToByteEncoder<CMPPMessage> {



    /** 写入头部占位（totalLength 先写 0），返回 totalLength 的写索引 */
    private int writeHeaderPlaceholder(ByteBuf out, int commandId, int sequenceId) {
        int idx = out.writerIndex();
        out.writeInt(0);           // totalLength 占位
        out.writeInt(commandId);
        out.writeInt(sequenceId);
        return idx;
    }

    /** 回填真实的消息总长度 */
    private void fillLength(ByteBuf out, int lengthIndex) {
        int totalLength = out.writerIndex() - lengthIndex;
        out.setInt(lengthIndex, totalLength);
    }


    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, CMPPMessage msg, ByteBuf out) throws Exception {
        int lengthIndex = writeHeaderPlaceholder(out, msg.getCommandId(), msg.getSequenceId());
        msg.writeBody(out);   // 多态分发，无 if / switch / Map
        fillLength(out, lengthIndex);
        byte[] bts = new byte[out.readableBytes()];
        out.copy().readBytes(bts);
        if (msg instanceof CMPPSubmit){
            System.out.println(11);
        }


    }
}