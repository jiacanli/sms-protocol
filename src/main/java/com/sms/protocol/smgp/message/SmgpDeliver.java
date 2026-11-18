package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
@Data
public class SmgpDeliver extends SmgpMessage {

    /* ============== Deliver 字段（规范 5.2.2.3.1） ============== */

    private String msgId;             // 10   短消息流水号（Octet String）

    /** 网关/挡板按「2 字节 0x00 + 8 字节 long」返回 MsgID 时的原始数值 */
    private Long msgIdLong;

    private byte isReport;          // 1     是否为状态报告（1=是，0=上行 MO）
    private byte msgFormat;         // 1     短消息格式
    private String receiveTime;     // 14    短消息接收时间
    private String srcTermId;        // 21    短消息发送号码
    private String destTermId;       // 21    短消息接收号码
    private short msgLength;         // 1     短消息内容长度（无符号）
    private byte[] msgContent;       // N     短消息内容
    private String reserve;          // 8     保留

    /* ============== 可选参数（TLV） ============== */
    private List<SmgpTlv> tlvs;

    /* ============== 当 isReport=1 时解析 ============== */

    private SmgpReport report;

    /* ============== 编码 ============== */

    @Override
    public int getBodyLength() {
        // 固定部分：10+1+1+14+21+21+1+8 = 77 + msgContent(N) + TLV
        int contentLen = msgContent == null ? 0 : msgContent.length;
        return 77 + contentLen + SmgpTlv.calcLength(tlvs);
    }

    @Override
    public void encodeBody(ByteBuf buf) {
        writeMsgId(buf, msgId, msgIdLong);
        buf.writeByte(isReport);
        buf.writeByte(msgFormat);
        writeFixedString(buf, receiveTime, 14);
        writeFixedString(buf, srcTermId, 21);
        writeFixedString(buf, destTermId, 21);
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

    /* ============== 解码 ============== */

    @Override
    public void decodeBody(ByteBuf buf) {
        MsgIdData data = readMsgId(buf);
        msgId = data.getText();
        msgIdLong = data.getAsLong();
        isReport = buf.readByte();
        msgFormat = buf.readByte();
        receiveTime = readFixedString(buf, 14);
        srcTermId = readFixedString(buf, 21);
        destTermId = readFixedString(buf, 21);
        msgLength = (short) buf.readUnsignedByte();
        msgContent = new byte[msgLength];
        buf.readBytes(msgContent);
        reserve = readFixedString(buf, 8);

        tlvs = SmgpTlv.parseAll(buf);

        if (isReport == 1) {
            report = SmgpReport.parse(msgContent, msgFormat & 0xFF);
        }
    }

    @Override
    public void initHeader() {
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_DELIVER, SequenceIdGenerator.next());
        this.setHeader(header);
    }

    /* ============== 文本解析 ============== */

    public String getMsgText() {
        if (isReport == 1) return null;
        if (msgContent == null) return null;
        if (msgFormat == 8) {
            return new String(msgContent, Charset.forName("UTF-16BE"));
        }
        if (msgFormat == 15) {
            return new String(msgContent, Charset.forName("GBK"));
        }
        return new String(msgContent, StandardCharsets.US_ASCII);
    }

    /**
     * 按当前 MsgFormat 编码并设置内容（同时修正 msgLength）。
     * 调用方应先 setMsgFormat，再调用本方法。
     */
    public void setMsgText(String text) {
        if (text == null) text = "";
        if (msgFormat == 8) {
            msgContent = text.getBytes(Charset.forName("UTF-16BE"));
        } else if (msgFormat == 15) {
            msgContent = text.getBytes(Charset.forName("GBK"));
        } else {
            msgContent = text.getBytes(StandardCharsets.US_ASCII);
        }
        msgLength = (short) msgContent.length;
    }

    /* ============== 工具方法 ============== */

    protected void writeFixedString(ByteBuf buf, String value, int length) {
        if (value == null) value = "";
        byte[] data = value.getBytes(StandardCharsets.US_ASCII);
        if (data.length >= length) {
            buf.writeBytes(data, 0, length);
        } else {
            buf.writeBytes(data);
            for (int i = data.length; i < length; i++) buf.writeByte(0);
        }
    }

    protected String readFixedString(ByteBuf buf, int length) {
        byte[] data = new byte[length];
        buf.readBytes(data);
        int i = 0;
        while (i < data.length && data[i] != 0) i++;
        return new String(data, 0, i);
    }

    /* Getter / Setter 可 Lombok */

    public SmgpReport getReport() {
        return report;
    }
}

