package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

/**
 * 查询短消息发送状态响应（Query_Resp，规范 5.2.2.7.2）。
 */
@Data
public class SmgpQueryResp extends SmgpMessage {

    private String queryTime;    // 8    查询时间
    private byte queryType;      // 1    查询类别
    private String queryCode;    // 10   查询码（业务类型）
    private int mtTlMsg;         // 4    接收短消息总数
    private int mtTlusr;         // 4    接收用户总数
    private int mtScs;           // 4    成功转发数量
    private int mtWt;            // 4    待转发数量
    private int mtFl;            // 4    转发失败数量
    private int moScs;           // 4    成功送达数量
    private int moWt;            // 4    待送达数量
    private int moFl;            // 4    送达失败数量
    private String reverse;      // 8    保留

    @Override
    public int getBodyLength() {
        // 8+1+10 + 4*8 + 8 = 59
        return 59;
    }

    @Override
    public void encodeBody(ByteBuf buf) {
        writeFixedString(buf, queryTime, 8);
        buf.writeByte(queryType);
        writeFixedString(buf, queryCode, 10);
        buf.writeInt(mtTlMsg);
        buf.writeInt(mtTlusr);
        buf.writeInt(mtScs);
        buf.writeInt(mtWt);
        buf.writeInt(mtFl);
        buf.writeInt(moScs);
        buf.writeInt(moWt);
        buf.writeInt(moFl);
        writeFixedString(buf, reverse, 8);
    }

    @Override
    public void decodeBody(ByteBuf buf) {
        queryTime = readFixedString(buf, 8);
        queryType = buf.readByte();
        queryCode = readFixedString(buf, 10);
        mtTlMsg = buf.readInt();
        mtTlusr = buf.readInt();
        mtScs = buf.readInt();
        mtWt = buf.readInt();
        mtFl = buf.readInt();
        moScs = buf.readInt();
        moWt = buf.readInt();
        moFl = buf.readInt();
        reverse = readFixedString(buf, 8);
    }

    @Override
    public void initHeader() {
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_QUERY_RESP, SequenceIdGenerator.next());
        this.setHeader(header);
    }

    /* Getter / Setter */
}
