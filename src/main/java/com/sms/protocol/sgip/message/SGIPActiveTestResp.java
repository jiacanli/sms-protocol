package com.sms.protocol.sgip.message;

// SGIPActiveTestResp.java


import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class SGIPActiveTestResp implements SGIPMessage {
    private SGIPHeader header;

    @Override
    public void writeBody(ByteBuf out) {
        // 无消息体
    }
}

