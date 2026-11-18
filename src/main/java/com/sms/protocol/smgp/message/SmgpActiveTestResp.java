package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;

import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;

/**
 * 链路检测响应（Active_Test_Resp）。
 * 规范 5.2.2.5.2：无消息体，仅 12 字节消息头。
 */
public class SmgpActiveTestResp extends SmgpMessage {

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
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_ACTIVE_TEST_RESP, SequenceIdGenerator.next());
        this.setHeader(header);
    }
}
