package com.sms.protocol.smgp.codec.mina;

import com.sms.protocol.smgp.message.SmgpMessage;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

/**
 * SMGP Mina 编码器（替代第三方 jar 的 ProtocolEncoder）。
 * <p>
 * 拆包/组包核心逻辑见 {@link SmgpCodecKit}，本类仅做 Mina 接口适配；
 * 生产如需要配置驱动的报文级切换，可在自定义 encoder 中 if/else 复用 {@link SmgpCodecKit}。
 */
public class SmgpMinaEncoder implements ProtocolEncoder {

    @Override
    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
        out.write(SmgpCodecKit.encodeFrame((SmgpMessage) message));
    }

    @Override
    public void dispose(IoSession session) throws Exception {
        // 无状态，无需释放资源
    }
}
