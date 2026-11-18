package com.sms.protocol.sgip.message;

import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class SGIPSubmitResp implements SGIPMessage {
    private SGIPHeader header;
    /** 处理结果：0=成功，非0=错误码，1字节 */
    private int result;
    /** 保留字段，8字节 */
    private byte[] reserve = new byte[8];

    @Override
    public void writeBody(ByteBuf out) {
        out.writeByte(result & 0xFF);
        out.writeBytes(reserve);
    }
}

