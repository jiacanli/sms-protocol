package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

/**
 * SMGW 之间转发短消息响应（Forward_Resp，规范 5.2.2.4.2）。
 */
@Data
public class SmgpForwardResp extends SmgpMessage {

    private String msgId;     // 10   SMGW 产生的短消息流水号

    /** 网关/挡板按「2 字节 0x00 + 8 字节 long」返回 MsgID 时的原始数值 */
    private Long msgIdLong;

    private int status;       // 4    请求返回结果

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
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_FORWARD_RESP, SequenceIdGenerator.next());
        this.setHeader(header);
    }

    /* Getter / Setter */
}
