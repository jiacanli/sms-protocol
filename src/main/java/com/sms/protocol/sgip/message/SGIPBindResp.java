package com.sms.protocol.sgip.message;



import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class SGIPBindResp implements SGIPMessage {
    private SGIPHeader header;
    /**
     * 处理结果
     * 0: 成功
     * 非0: 对应错误码
     */
    private int result;
    /** 保留字段，固定8字节 */
    private byte[] reserve = new byte[8];

    @Override
    public void writeBody(ByteBuf out) {
        // 1字节结果码（无符号处理）
        out.writeByte(result & 0xFF);
        // 8字节保留字段
        out.writeBytes(reserve);
    }
}

