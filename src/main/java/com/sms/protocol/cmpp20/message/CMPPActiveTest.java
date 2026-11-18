package com.sms.protocol.cmpp20.message;

import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class CMPPActiveTest implements CMPPMessage{
    @Override
    public void writeBody(ByteBuf out) {

    }

    private CMPPHeader header;
    // 无消息体
    // getters and setters ...
}