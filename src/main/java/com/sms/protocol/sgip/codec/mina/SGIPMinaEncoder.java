package com.sms.protocol.sgip.codec.mina;

import com.sms.protocol.sgip.message.SGIPMessage;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

/**
 * SGIP Mina 编码器（替代第三方 jar 的 ProtocolEncoder）。
 * <p>
 * 组包核心逻辑见 {@link SGIPCodecKit}，本类仅做 Mina 接口适配。
 */
public class SGIPMinaEncoder implements ProtocolEncoder {

    @Override
    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
        out.write(SGIPCodecKit.encodeFrame((SGIPMessage) message));
    }

    @Override
    public void dispose(IoSession session) throws Exception {
        // 无状态，无需释放资源
    }
}
