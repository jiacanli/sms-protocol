package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class SmgpDeliverResp extends SmgpMessage {

    private String msgId;     // 10

    /** 网关/挡板按「2 字节 0x00 + 8 字节 long」返回 MsgID 时的原始数值 */
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
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_DELIVER_RESP, SequenceIdGenerator.next());
        this.setHeader(header);
    }

    /* Getter / Setter */
}

