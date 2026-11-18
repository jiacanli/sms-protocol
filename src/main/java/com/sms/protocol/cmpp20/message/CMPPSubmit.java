package com.sms.protocol.cmpp20.message;

import com.sms.protocol.utils.CMPPCodecUtil;
import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class CMPPSubmit implements CMPPMessage{
    private CMPPHeader header;
    private long msgId;             // 8字节 (Msg_Id)
    private int pkTotal;            // 1字节
    private int pkNumber;           // 1字节
    private int registeredDelivery; // 1字节
    private int msgLevel;           // 1字节
    private String serviceId;       // 10字节
    private int feeUserType;        // 1字节
    private String feeTerminalId;   // 21字节
    private int tpPid;              // 1字节
    private int tpUdhi;             // 1字节
    private int msgFmt;             // 1字节
    private String msgSrc;          // 6字节
    private String feeType;         // 2字节
    private String feeCode;         // 6字节
    private String valIdTime;       // 17字节
    private String atTime;          // 17字节
    private String srcId;           // 21字节
    private int destUsrTl;          // 1字节
    private String[] destTerminalId; // 每个21字节
    private int msgLength;          // 1字节
    private byte[] msgContent;      // 变长
    private byte[] reserve = new byte[8];

    @Override
    public void writeBody(ByteBuf out) {
        out.writeLong(msgId);
        out.writeByte(pkTotal);
        out.writeByte(pkNumber);
        out.writeByte(registeredDelivery);
        out.writeByte(msgLevel);

        CMPPCodecUtil.writeCString(out, serviceId, 10);
        out.writeByte(feeUserType);
        CMPPCodecUtil.writeCString(out, feeTerminalId, 21);

        out.writeByte(tpPid);
        out.writeByte(tpUdhi);
        out.writeByte(msgFmt);

        CMPPCodecUtil.writeCString(out, msgSrc, 6);
        CMPPCodecUtil.writeCString(out, feeType, 2);
        CMPPCodecUtil.writeCString(out, feeCode, 6);
        CMPPCodecUtil.writeCString(out, valIdTime, 17);
        CMPPCodecUtil.writeCString(out, atTime, 17);
        CMPPCodecUtil.writeCString(out, srcId, 21);

        out.writeByte(destUsrTl);
        for (String dest : destTerminalId) {
            CMPPCodecUtil.writeCString(out, dest, 21);
        }

        out.writeByte(msgLength);
        out.writeBytes(msgContent);
        out.writeBytes(reserve);
    }
}
