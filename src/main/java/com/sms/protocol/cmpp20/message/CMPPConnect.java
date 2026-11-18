package com.sms.protocol.cmpp20.message;

import com.sms.protocol.utils.CMPPCodecUtil;
import com.sms.protocol.utils.Cmpp2AuthCalculator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

import java.security.NoSuchAlgorithmException;

@Data
public class CMPPConnect implements CMPPMessage{
    private CMPPHeader header;
    private String sourceAddr;       // 6字节，C-Octet String
    private byte[] authenticatorSource; // 16字节
    private byte version;            // CMPP 2.0 固定为 0x20
    private int timestamp;           // MMDDHHMMSS 格式的整数

    @Override
    public void writeBody(ByteBuf out) {
        CMPPCodecUtil.writeCString(out, sourceAddr, 6);
        out.writeBytes(authenticatorSource); // 固定 16 字节
        out.writeByte(version);
        out.writeInt(timestamp);
    }


    public void build(String sourceAddr, String sharedSecret) {
        this.sourceAddr = sourceAddr;
        this.version = 0x20;
        this.timestamp = Cmpp2AuthCalculator.generateTimestamp();
        try {
            this.authenticatorSource = Cmpp2AuthCalculator.calcAuthenticatorSource(sourceAddr, sharedSecret,timestamp);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
