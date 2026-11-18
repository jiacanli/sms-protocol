package com.sms.protocol.smgp.codec.mina;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;

/**
 * SMGP Mina 解码器（替代第三方 jar 的 ProtocolDecoder）。
 * <p>
 * 拆包/组包核心逻辑见 {@link SmgpCodecKit}，本类仅做 Mina 接口适配；
 * 生产如需要配置驱动的报文级切换，可在自定义 decoder 中 if/else 复用 {@link SmgpCodecKit}。
 */
public class SmgpMinaDecoder implements ProtocolDecoder {

    @Override
    public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
        SmgpCodecKit.decodeFrames(session, in, out::write);
    }

    @Override
    public void finishDecode(IoSession session, ProtocolDecoderOutput out) throws Exception {
        // 无残留状态，无需处理
    }

    @Override
    public void dispose(IoSession session) throws Exception {
        // 无状态，无需释放资源
    }
}
