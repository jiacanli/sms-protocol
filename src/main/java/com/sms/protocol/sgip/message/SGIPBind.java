package com.sms.protocol.sgip.message;


import io.netty.buffer.ByteBuf;
import lombok.Data;
import com.sms.protocol.utils.SGIPCodecUtil;

@Data
public class SGIPBind implements SGIPMessage {
    private SGIPHeader header;
    /** 登录类型：1=SP连SMG 2=SMG互联 3=SMG连SMSC */
    private int loginType;
    /** 登录账号，16字节定长 */
    private String name;
    /** 登录密码，16字节定长 */
    private String password;
    /** 保留字段，固定8字节 */
    private byte[] reserve = new byte[8];

    @Override
    public void writeBody(ByteBuf out) {
        out.writeByte(loginType & 0xFF);
        SGIPCodecUtil.writeCString(out, name, 16);
        SGIPCodecUtil.writeCString(out, password, 16);
        out.writeBytes(reserve);
    }
}
