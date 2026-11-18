package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;

/**
 * 注销响应（Exit_Resp）。
 * 规范 5.2.2.6.2：无消息体，仅 12 字节消息头。
 */
public class SmgpExitResp extends SmgpMessage {

    @Override
    public int getBodyLength() {
        return 0;
    }

    @Override
    public void encodeBody(ByteBuf buf) {
        // 无消息体
    }

    @Override
    public void decodeBody(ByteBuf buf) {
        // 无消息体
    }

    @Override
    public void initHeader() {
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_EXIT_RESP, SequenceIdGenerator.next());
        this.setHeader(header);
    }
}
