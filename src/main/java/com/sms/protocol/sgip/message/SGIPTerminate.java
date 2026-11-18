package com.sms.protocol.sgip.message;



import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class SGIPTerminate implements SGIPMessage {
    private SGIPHeader header;

    @Override
    public void writeBody(ByteBuf out) {
        // 无消息体
    }
}
