package com.sms.protocol.cmpp20.message;

import com.sms.protocol.utils.CMPPCodecUtil;
import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class CMPPDeliver implements CMPPMessage{
    private CMPPHeader header;
    private long msgId;             // 8字节
    private String destId;          // 21字节
    private String serviceId;       // 10字节
    private int tpPid;              // 1字节
    private int tpUdhi;             // 1字节
    private int msgFmt;             // 1字节
    private String srcTerminalId;   // 21字节
    private int registeredDelivery; // 1字节，1=状态报告
    private int msgLength;          // 1字节
    private byte[] msgContent;      // 变长
    private byte[] reserve = new byte[8]; // 新增：8字节保留字段

    // 下面是状态报告解析后的字段（当 registeredDelivery == 1 时有效）
    private Long reportMsgId;       // 状态报告对应的 Msg_Id
    private String stat;            // 状态："DELIVRD"、"EXPIRED" 等
    private String submitTime;      // 提交时间
    private String doneTime;        // 完成时间
    private String destTerminalId;  // 接收方号码
    private Integer smscSequence;   // SMSC 序号

    @Override
    public void writeBody(ByteBuf out) {
        out.writeLong(msgId);
        CMPPCodecUtil.writeCString(out, destId, 21);
        CMPPCodecUtil.writeCString(out, serviceId, 10);
        out.writeByte(tpPid);
        out.writeByte(tpUdhi);
        out.writeByte(msgFmt);
        CMPPCodecUtil.writeCString(out, srcTerminalId, 21);
        out.writeByte(registeredDelivery);

        out.writeByte(msgLength);
        out.writeBytes(msgContent);
        out.writeBytes(reserve);
    }

    // getters and setters ...
}
