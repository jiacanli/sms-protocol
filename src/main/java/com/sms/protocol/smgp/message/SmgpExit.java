package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;

/**
 * 注销请求（Exit）。
 * 规范 5.2.2.6.1：无消息体，仅 12 字节消息头。
 * 客户端/服务器端在决定拆除当前应用层连接时发送，随后应重新 Login 才能继续通信。
 */
public class SmgpExit extends SmgpMessage {

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
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_EXIT, SequenceIdGenerator.next());
        this.setHeader(header);
    }
}
