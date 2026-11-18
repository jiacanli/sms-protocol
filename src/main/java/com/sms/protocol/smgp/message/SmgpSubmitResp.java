package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class SmgpSubmitResp extends SmgpMessage {

    /**
     * 短消息流水号，10 字节 Octet String（规范 6.2.26）。
     * 兼容两种编码：ASCII 定长字符串；或「2 字节 0x00 + 8 字节 long」。
     */
    private String msgId;     // 10

    /** 当网关/挡板按「2 字节 0x00 + 8 字节 long」返回 MsgID 时的原始数值（网络序大端） */
    private Long msgIdLong;

    private int status;     // 4

    @Override
    public int getBodyLength() {
        // MsgID(10) + Status(4)
        return 14;
    }

    @Override
    public void encodeBody(ByteBuf buf) {
        writeMsgId(buf, msgId, msgIdLong);
        buf.writeInt(status);
    }

    @Override
    public void decodeBody(ByteBuf buf) {
        MsgIdData data = readMsgId(buf);
        msgId = data.getText();
        msgIdLong = data.getAsLong();
        status = buf.readInt();
    }

    @Override
    public void initHeader() {
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_SUBMIT_RESP, SequenceIdGenerator.next());
        this.setHeader(header);
    }

    /* Getter / Setter */
}
