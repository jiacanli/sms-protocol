package com.sms.protocol.cmpp20.message;

import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class CMPPDeliverResp implements CMPPMessage{
    private CMPPHeader header;
    private long msgId;   // 8字节
    private int result;   // 4字节，0=成功

    @Override
    public void writeBody(ByteBuf out) {
        out.writeLong(msgId);
        out.writeInt(result);
    }
    // getters and setters ...
}
