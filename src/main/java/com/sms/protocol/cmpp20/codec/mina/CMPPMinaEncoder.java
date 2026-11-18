package com.sms.protocol.cmpp20.codec.mina;

import com.sms.protocol.cmpp20.message.CMPPMessage;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

/**
 * CMPP Mina 编码器（替代第三方 jar 的 ProtocolEncoder）。
 * <p>
 * 组包核心逻辑见 {@link CMPPCodecKit}，本类仅做 Mina 接口适配。
 */
public class CMPPMinaEncoder implements ProtocolEncoder {

    @Override
    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
        out.write(CMPPCodecKit.encodeFrame((CMPPMessage) message));
    }

    @Override
    public void dispose(IoSession session) throws Exception {
        // 无状态，无需释放资源
    }
}
