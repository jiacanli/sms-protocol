package com.sms.protocol.smgp.codec.mina;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.smgp.SmgpMessageFactory;
import com.sms.protocol.smgp.message.SmgpMessage;
import com.sms.protocol.utils.BufferBridge;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SMGP 编解码工具类（自研实现核心逻辑）。
 * <p>
 * 用途：把自研编解码逻辑提成静态方法，生产现有（三方 jar）decoder/encoder 内通过 if/else
 * 按配置实时切换调用，实现<b>报文级动态切换</b>——配置变更后下一个包即生效，无需关闭连接：
 * <pre>{@code
 * public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
 *     if (codecSwitch.current().isNew()) {
 *         SmgpCodecKit.decodeFrames(session, in, out::write);   // 新实现
 *         return;
 *     }
 *     // ...原三方 jar 逻辑...
 * }
 * }</pre>
 * {@link SmgpMinaDecoder} / {@link SmgpMinaEncoder} 亦复用本工具类，保证两处行为一致。
 */
public final class SmgpCodecKit {

    private static final Logger logger = LoggerFactory.getLogger(SmgpCodecKit.class);

    /** 单个 SMGP 数据包的最大长度（防呆） */
    private static final int MAX_PACKET_LENGTH = 64 * 1024;

    private SmgpCodecKit() {
    }

    /**
     * 解码（拆包）：从 Mina 累积缓冲中消费全部完整 SMGP 帧并逐帧回调。
     * 半包回退（mark/reset）等待累积；包长非法关闭会话；未知命令字/解析异常丢弃不中断。
     *
     * @param session  会话（非法包长时用于关闭），可为 null（跳过关闭）
     * @param in       Mina 累积缓冲（与 ProtocolDecoder.decode 入参同一引用）
     * @param consumer 每解出一帧回调一次（如 ProtocolDecoderOutput::write）
     */
    public static void decodeFrames(IoSession session, IoBuffer in, SmgpFrameConsumer consumer) {
        while (true) {
            // 消息头（12 字节）不足则等待累积
            if (in.remaining() < SmgpConstants.HEADER_LENGTH) {
                return;
            }

            in.mark();
            int totalLength = in.getInt();

            // 包长合法性校验
            if (totalLength < SmgpConstants.HEADER_LENGTH || totalLength > MAX_PACKET_LENGTH) {
                logger.warn("Invalid SMGP packet length: {}, closing session", totalLength);
                if (session != null) {
                    session.closeNow();
                }
                return;
            }

            // 半包：回退读指针，等待后续数据累积
            if (in.remaining() < totalLength - 4) {
                in.reset();
                return;
            }

            int commandId = in.getInt();
            int sequenceId = in.getInt();

            try {
                SmgpMessage msg = SmgpMessageFactory.create(commandId);
                msg.getHeader().setPacketLength(totalLength);
                msg.getHeader().setCommandId(commandId);
                msg.getHeader().setSequenceId(sequenceId);
                msg.decodeBody(BufferBridge.toNetty(in, totalLength - SmgpConstants.HEADER_LENGTH));
                consumer.accept(msg);
            } catch (Exception e) {
                // 未知命令字或包体解析异常：记录日志并丢弃该包，避免整个连接中断
                logger.warn("Failed to decode SMGP packet, commandId={}, seq={}: {}",
                        String.format("0x%08X", commandId), sequenceId, e.getMessage());
            }
        }
    }

    /**
     * 编码（组包）：SmgpMessage → 完整网络包 IoBuffer（12 字节头 + body）。
     */
    public static IoBuffer encodeFrame(SmgpMessage msg) {
        ByteBuf bodyBuf = Unpooled.buffer();
        msg.encodeBody(bodyBuf);

        int totalLength = SmgpConstants.HEADER_LENGTH + bodyBuf.readableBytes();
        IoBuffer io = IoBuffer.allocate(totalLength);
        io.putInt(totalLength);
        io.putInt(msg.getHeader().getCommandId());
        io.putInt(msg.getHeader().getSequenceId());
        BufferBridge.writeTo(io, bodyBuf);
        io.flip();
        return io;
    }

    /** 解码帧回调 */
    @FunctionalInterface
    public interface SmgpFrameConsumer {
        void accept(SmgpMessage message);
    }
}
