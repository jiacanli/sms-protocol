package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SMGW 之间转发短消息请求（Forward，规范 5.2.2.4.1）。
 * 用于源 SMGW 向目的 SMGW 转发短消息，SP 端一般不直接使用。
 */
@Data
public class SmgpForward extends SmgpMessage {

    /* ============== 必选字段 ============== */

    private String msgId;            // 10   短消息流水号

    /** 网关/挡板按「2 字节 0x00 + 8 字节 long」返回 MsgID 时的原始数值 */
    private Long msgIdLong;

    private String destSmgwNo;       // 6    目的 SMGW 代码
    private String srcSmgwNo;        // 6    源 SMGW 代码
    private String smcNo;            // 6    短消息中心代码
    private byte msgType;            // 1    短消息类型
    private byte reportFlag;         // 1    状态报告标志
    private byte priority;           // 1    发送优先级
    private String serviceId;        // 10   业务代码
    private String feeType;          // 2    资费类型
    private String feeCode;          // 6    资费代码
    private String fixedFee;         // 6    包月费/封顶费
    private byte msgFormat;          // 1    短消息格式
    private String validTime;        // 17   有效时间
    private String atTime;           // 17   定时发送时间
    private String srcTermId;        // 21   发送用户号码
    private String destTermId;       // 21   接收用户号码
    private String chargeTermId;     // 21   计费用户号码
    private short msgLength;         // 1    内容长度（无符号）
    private byte[] msgContent;       // N    短消息内容
    private String reserve;          // 8    保留

    /* ============== 可选参数（TLV） ============== */
    private List<SmgpTlv> tlvs;

    @Override
    public int getBodyLength() {
        // 固定部分：10+6+6+6+1+1+1+10+2+6+6+1+17+17+21+21+21+1+8 = 162
        int contentLen = msgContent == null ? 0 : msgContent.length;
        return 162 + contentLen + SmgpTlv.calcLength(tlvs);
    }

    @Override
    public void encodeBody(ByteBuf buf) {
        writeMsgId(buf, msgId, msgIdLong);
        writeFixedString(buf, destSmgwNo, 6);
        writeFixedString(buf, srcSmgwNo, 6);
        writeFixedString(buf, smcNo, 6);
        buf.writeByte(msgType);
        buf.writeByte(reportFlag);
        buf.writeByte(priority);
        writeFixedString(buf, serviceId, 10);
        writeFixedString(buf, feeType, 2);
        writeFixedString(buf, feeCode, 6);
        writeFixedString(buf, fixedFee, 6);
        buf.writeByte(msgFormat);
        writeFixedString(buf, validTime, 17);
        writeFixedString(buf, atTime, 17);
        writeFixedString(buf, srcTermId, 21);
        writeFixedString(buf, destTermId, 21);
        writeFixedString(buf, chargeTermId, 21);
        buf.writeByte(msgLength);
        if (msgContent != null) {
            buf.writeBytes(msgContent);
        }
        writeFixedString(buf, reserve, 8);

        if (tlvs != null) {
            for (SmgpTlv tlv : tlvs) {
                tlv.encode(buf);
            }
        }
    }

    @Override
    public void decodeBody(ByteBuf buf) {
        MsgIdData data = readMsgId(buf);
        msgId = data.getText();
        msgIdLong = data.getAsLong();
        destSmgwNo = readFixedString(buf, 6);
        srcSmgwNo = readFixedString(buf, 6);
        smcNo = readFixedString(buf, 6);
        msgType = buf.readByte();
        reportFlag = buf.readByte();
        priority = buf.readByte();
        serviceId = readFixedString(buf, 10);
        feeType = readFixedString(buf, 2);
        feeCode = readFixedString(buf, 6);
        fixedFee = readFixedString(buf, 6);
        msgFormat = buf.readByte();
        validTime = readFixedString(buf, 17);
        atTime = readFixedString(buf, 17);
        srcTermId = readFixedString(buf, 21);
        destTermId = readFixedString(buf, 21);
        chargeTermId = readFixedString(buf, 21);
        msgLength = (short) buf.readUnsignedByte();
        msgContent = new byte[msgLength];
        buf.readBytes(msgContent);
        reserve = readFixedString(buf, 8);

        tlvs = SmgpTlv.parseAll(buf);
    }

    @Override
    public void initHeader() {
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_FORWARD, SequenceIdGenerator.next());
        this.setHeader(header);
    }

    /* ============== 文本转换（与 Submit/Deliver 保持一致） ============== */

    public String getMsgText() {
        if (msgContent == null) return null;
        if (msgFormat == SmgpConstants.MSG_FORMAT_UCS2) {
            return new String(msgContent, StandardCharsets.UTF_16BE);
        }
        if (msgFormat == SmgpConstants.MSG_FORMAT_GBK) {
            return new String(msgContent, java.nio.charset.Charset.forName("GBK"));
        }
        return new String(msgContent, StandardCharsets.US_ASCII);
    }

    public void setMsgText(String text) {
        if (text == null) text = "";
        if (msgFormat == SmgpConstants.MSG_FORMAT_UCS2) {
            msgContent = text.getBytes(StandardCharsets.UTF_16BE);
        } else if (msgFormat == SmgpConstants.MSG_FORMAT_GBK) {
            msgContent = text.getBytes(java.nio.charset.Charset.forName("GBK"));
        } else {
            msgContent = text.getBytes(StandardCharsets.US_ASCII);
        }
        msgLength = (short) msgContent.length;
    }

    /* Getter / Setter */
}
