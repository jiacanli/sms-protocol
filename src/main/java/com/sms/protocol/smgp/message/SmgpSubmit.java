package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class SmgpSubmit extends SmgpMessage {

    /* ================= 必选字段（顺序与规范 5.2.2.2.1 一致） ================= */

    private byte msgType;              // 1    短消息类型（MT=6，MO=0，点对点=7）
    private byte needReport;           // 1    是否要求状态报告（0 不要求，1 要求）
    private byte priority;             // 1    发送优先级
    private String serviceId;           // 10   业务代码
    private String feeType;             // 2    收费类型
    private String feeCode;             // 6    资费代码
    private String fixedFee;            // 6    包月费/封顶费
    private byte msgFormat;             // 1    短消息格式（0 ASCII / 8 UCS2 / 15 GBK）
    private String validTime;           // 17   短消息有效时间
    private String atTime;              // 17   定时发送时间
    private String srcTermId;            // 21   发送方号码（接入号）
    private String chargeTermId;         // 21   计费用户号码
    private byte destTermIdCount;        // 1    接收号码总数
    private List<String> destTermIds;    // 21 * N 接收号码
    private short msgLength;             // 1    短消息内容长度（无符号）
    private byte[] msgContent;           // N    短消息内容
    private String reserve;              // 8    保留

    /* ================= 可选参数（TLV，规范 6.3 节） ================= */
    private List<SmgpTlv> tlvs;

    /* ================= 编码 ================= */

    @Override
    public int getBodyLength() {
        // 固定部分：1+1+1+10+2+6+6+1+17+17+21+21+1 = 105
        // + destTermIds*21 + msgLength(1) + msgContent(N) + reserve(8) + TLV
        int destCount = destTermIds == null ? 0 : destTermIds.size();
        int contentLen = msgContent == null ? 0 : msgContent.length;
        return 105 + destCount * 21 + 1 + contentLen + 8 + SmgpTlv.calcLength(tlvs);
    }

    @Override
    public void encodeBody(ByteBuf buf) {
        buf.writeByte(msgType);
        buf.writeByte(needReport);
        buf.writeByte(priority);
        writeFixedString(buf, serviceId, 10);
        writeFixedString(buf, feeType, 2);
        writeFixedString(buf, feeCode, 6);
        writeFixedString(buf, fixedFee, 6);
        buf.writeByte(msgFormat);
        writeFixedString(buf, validTime, 17);
        writeFixedString(buf, atTime, 17);
        writeFixedString(buf, srcTermId, 21);
        writeFixedString(buf, chargeTermId, 21);
        buf.writeByte(destTermIdCount);

        if (destTermIds != null) {
            for (String dest : destTermIds) {
                writeFixedString(buf, dest, 21);
            }
        }

        buf.writeByte(msgLength);
        if (msgContent != null) {
            buf.writeBytes(msgContent);
        }
        writeFixedString(buf, reserve, 8);

        // 可选参数：TLV（PkTotal/PkNumber 等长短信分片信息在此携带）
        if (tlvs != null) {
            for (SmgpTlv tlv : tlvs) {
                tlv.encode(buf);
            }
        }
    }

    /* ================= 解码 ================= */

    @Override
    public void decodeBody(ByteBuf buf) {
        msgType = buf.readByte();
        needReport = buf.readByte();
        priority = buf.readByte();
        serviceId = readFixedString(buf, 10);
        feeType = readFixedString(buf, 2);
        feeCode = readFixedString(buf, 6);
        fixedFee = readFixedString(buf, 6);
        msgFormat = buf.readByte();
        validTime = readFixedString(buf, 17);
        atTime = readFixedString(buf, 17);
        srcTermId = readFixedString(buf, 21);
        chargeTermId = readFixedString(buf, 21);
        destTermIdCount = buf.readByte();

        destTermIds = new ArrayList<>();
        for (int i = 0; i < (destTermIdCount & 0xFF); i++) {
            destTermIds.add(readFixedString(buf, 21));
        }

        msgLength = (short) buf.readUnsignedByte();
        msgContent = new byte[msgLength];
        buf.readBytes(msgContent);
        reserve = readFixedString(buf, 8);

        tlvs = SmgpTlv.parseAll(buf);
    }

    @Override
    public void initHeader() {
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_SUBMIT, SequenceIdGenerator.next());
        this.setHeader(header);
    }

    /* ================= 工具方法 ================= */

    protected void writeFixedString(ByteBuf buf, String value, int length) {
        if (value == null) value = "";
        byte[] data = value.getBytes(StandardCharsets.US_ASCII);
        if (data.length >= length) {
            buf.writeBytes(data, 0, length);
        } else {
            buf.writeBytes(data);
            for (int i = data.length; i < length; i++) {
                buf.writeByte(0);
            }
        }
    }

    protected String readFixedString(ByteBuf buf, int length) {
        byte[] data = new byte[length];
        buf.readBytes(data);
        int i = 0;
        while (i < data.length && data[i] != 0) i++;
        return new String(data, 0, i, StandardCharsets.US_ASCII);
    }

    /* ================= 文本转换 ================= */

    /**
     * 按 MsgFormat 解码消息内容：8=UCS2(UTF-16BE)，15=GBK，其余按 ASCII。
     */
    public String getMsgText() {
        if (msgContent == null) return null;
        if (msgFormat == SmgpConstants.MSG_FORMAT_UCS2) {
            return new String(msgContent, Charset.forName("UTF-16BE"));
        }
        if (msgFormat == SmgpConstants.MSG_FORMAT_GBK) {
            return new String(msgContent, Charset.forName("GBK"));
        }
        return new String(msgContent, StandardCharsets.US_ASCII);
    }

    /**
     * 按当前 MsgFormat 编码并设置内容，同时修正 msgLength。
     * 调用方应先 setMsgFormat，再调用本方法。
     */
    public void setMsgText(String text) {
        if (text == null) text = "";
        if (msgFormat == SmgpConstants.MSG_FORMAT_UCS2) {
            msgContent = text.getBytes(Charset.forName("UTF-16BE"));
        } else if (msgFormat == SmgpConstants.MSG_FORMAT_GBK) {
            msgContent = text.getBytes(Charset.forName("GBK"));
        } else {
            msgContent = text.getBytes(StandardCharsets.US_ASCII);
        }
        msgLength = (short) msgContent.length;
    }

    /* ================= 长短信分片信息（TLV 方式） ================= */

    /**
     * 设置长短信分片信息（PkTotal/PkNumber，TLV 可选参数）。
     * Tag 编号需按对接网关文档填写，参考规范 6.3.10 / 6.3.11。
     */
    public void setPk(int pkTotalTag, int pkNumberTag, int pkTotal, int pkNumber) {
        if (tlvs == null) tlvs = new ArrayList<>();
        tlvs.add(new SmgpTlv(pkTotalTag, pkTotal));
        tlvs.add(new SmgpTlv(pkNumberTag, pkNumber));
    }

    /* ================= 便捷构造 ================= */

    /**
     * 构造一条普通 MT 短信（SP -> 终端）。
     * 规范中 MsgType=6 为 MT 消息；中文内容使用 UCS2（msgFormat=8）。
     */
    public static SmgpSubmit buildNormalSms(String src, String dest, String text) {
        SmgpSubmit s = new SmgpSubmit();
        s.setMsgType(SmgpConstants.MSG_TYPE_MT);          // 6 = MT
        s.setNeedReport((byte) 1);
        s.setPriority((byte) 0);
        s.setServiceId("test");
        s.setFeeType("01");
        s.setFeeCode("000000");
        s.setFixedFee("000000");
        s.setMsgFormat(SmgpConstants.MSG_FORMAT_UCS2);     // 8 = UCS2（中文）
        s.setSrcTermId(src);
        s.setChargeTermId(src);
        s.setDestTermIdCount((byte) 1);
        s.setDestTermIds(Collections.singletonList(dest));
        s.setReserve("");
        s.setMsgText(text);
        return s;
    }

    @Override
    public String toString() {
        return "SmgpSubmit{" +
                "msgType=" + msgType +
                ", needReport=" + needReport +
                ", priority=" + priority +
                ", serviceId='" + serviceId + '\'' +
                ", feeType='" + feeType + '\'' +
                ", feeCode='" + feeCode + '\'' +
                ", fixedFee='" + fixedFee + '\'' +
                ", msgFormat=" + msgFormat +
                ", srcTermId='" + srcTermId + '\'' +
                ", chargeTermId='" + chargeTermId + '\'' +
                ", destTermIds=" + destTermIds +
                ", tlvs=" + (tlvs == null ? null : tlvs.size()) +
                ", text='" + getMsgText() + '\'' +
                '}';
    }

    /* Getter / Setter 可自行生成 */
}
