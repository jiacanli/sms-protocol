package com.sms.protocol.sgip.message;



import com.sms.protocol.utils.SGIPCodecUtil;
import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class SGIPDeliver implements SGIPMessage {
    private SGIPHeader header;

    /** 接收方号码（SP接入号），21字节 */
    private String userNumber;
    /** SP号码，21字节 */
    private String spNumber;
    /** TP协议标识，1字节 */
    private int tpPid;
    /** TP用户数据头标识，1字节 */
    private int tpUdhi;
    /** 消息编码格式，1字节：0=ASCII 8=UCS2 15=GBK */
    private int msgFormat;
    /** 发送方终端号码，21字节 */
    private String srcTerminalId;
    /** 消息内容长度，1字节 */
    private int msgLength;
    /** 消息内容，变长 */
    private byte[] msgContent;
    /** 保留字段，8字节 */
    private byte[] reserve = new byte[8];

    @Override
    public void writeBody(ByteBuf out) {
        SGIPCodecUtil.writeCString(out, userNumber, 21);
        SGIPCodecUtil.writeCString(out, spNumber, 21);
        out.writeByte(tpPid & 0xFF);
        out.writeByte(tpUdhi & 0xFF);
        out.writeByte(msgFormat & 0xFF);
        SGIPCodecUtil.writeCString(out, srcTerminalId, 21);
        out.writeByte(msgLength & 0xFF);
        out.writeBytes(msgContent);
        out.writeBytes(reserve);
    }
}

