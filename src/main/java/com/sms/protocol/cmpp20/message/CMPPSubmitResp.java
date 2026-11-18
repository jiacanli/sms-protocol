package com.sms.protocol.cmpp20.message;

import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class CMPPSubmitResp implements CMPPMessage{
    private CMPPHeader header;
    private long msgId;   // 8字节
    private int result;   // 用int存值（兼容无符号字节0~255），但写入时只写1字节

    @Override
    public void writeBody(ByteBuf out) {
        out.writeLong(msgId);
        out.writeByte(result & 0xFF);
    }
    // getters and setters ...
}
