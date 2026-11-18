package com.sms.protocol.cmpp20.message;

import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class CMPPConnectResp implements CMPPMessage{
    private CMPPHeader header;
    private byte status;              // 0=成功，非0=失败
    private byte[] authenticatorISMG; // 16字节，ISMG 生成的认证串
    private byte version;             // CMPP 2.0 为 0x20

    @Override
    public void writeBody(ByteBuf out) {
        out.writeByte(status);
        out.writeBytes(authenticatorISMG);
        out.writeByte(version);
    }

    // getters and setters ...
}