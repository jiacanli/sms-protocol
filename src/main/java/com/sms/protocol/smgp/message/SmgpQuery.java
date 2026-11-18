package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

/**
 * 查询短消息发送状态请求（Query，规范 5.2.2.7.1，可选操作）。
 * SP 向 SMGW 查询某条短消息的发送状态。
 */
@Data
public class SmgpQuery extends SmgpMessage {

    private String queryTime;    // 8    查询时间
    private byte queryType;      // 1    查询类别
    private String queryCode;    // 10   查询码

    @Override
    public int getBodyLength() {
        // QueryTime(8) + QueryType(1) + QueryCode(10)
        return 19;
    }

    @Override
    public void encodeBody(ByteBuf buf) {
        writeFixedString(buf, queryTime, 8);
        buf.writeByte(queryType);
        writeFixedString(buf, queryCode, 10);
    }

    @Override
    public void decodeBody(ByteBuf buf) {
        queryTime = readFixedString(buf, 8);
        queryType = buf.readByte();
        queryCode = readFixedString(buf, 10);
    }

    @Override
    public void initHeader() {
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_QUERY, SequenceIdGenerator.next());
        this.setHeader(header);
    }

    /* Getter / Setter */
}
