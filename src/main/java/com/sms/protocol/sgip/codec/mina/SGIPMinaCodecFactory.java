package com.sms.protocol.sgip.codec.mina;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;

/**
 * SGIP Mina 编解码工厂（替代第三方 jar 的 ProtocolCodecFactory）。
 * <p>
 * 生产环境（Mina 框架）接入方式：
 * <pre>{@code
 * connector.getFilterChain().addLast("codec",
 *         new ProtocolCodecFilter(new SGIPMinaCodecFactory()));
 * }</pre>
 * 实例无状态，可安全复用。
 */
public class SGIPMinaCodecFactory implements ProtocolCodecFactory {

    private final ProtocolEncoder encoder = new SGIPMinaEncoder();
    private final ProtocolDecoder decoder = new SGIPMinaDecoder();

    @Override
    public ProtocolEncoder getEncoder(IoSession session) throws Exception {
        return encoder;
    }

    @Override
    public ProtocolDecoder getDecoder(IoSession session) throws Exception {
        return decoder;
    }
}
