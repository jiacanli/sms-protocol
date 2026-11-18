package com.sms.protocol.cmpp20.message;

import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class CMPPActiveTestResp implements CMPPMessage {
    private CMPPHeader header;
    private byte reserved; // 1字节保留字段

    @Override
    public void writeBody(ByteBuf out) {
        out.writeByte(reserved);
    }
    // getters and setters ...
}
