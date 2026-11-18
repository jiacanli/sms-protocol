package com.sms.protocol.cmpp20.codec.mina;

import com.sms.protocol.cmpp20.codec.CMPPDecoder;
import com.sms.protocol.cmpp20.message.*;
import com.sms.protocol.utils.BufferBridge;
import com.sms.protocol.utils.CMPPCodecUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CMPP 编解码工具类（自研实现核心逻辑，与 SMGP 的 {@code SmgpCodecKit} 同模式）。
 * <p>
 * 拆包/解析/组包核心逻辑集中在此，供两处复用：
 * <ul>
 *   <li>Mina 生产侧：decoder/encoder 内 if/else 切换时调用 {@link #decodeFrames} / {@link #encodeFrame}，
 *       实现报文级动态切换（配置变更后下一个包即生效，无需关闭连接）；</li>
 *   <li>Netty 侧：{@link CMPPDecoder} 复用 {@link #parseFrame}，解析逻辑只有一份。</li>
 * </ul>
 */
public final class CMPPCodecKit {

    private static final Logger logger = LoggerFactory.getLogger(CMPPCodecKit.class);

    /** CMPP 消息头固定长度：12 字节 */
    private static final int HEADER_LENGTH = 12;

    /** 单个 CMPP 数据包的最大长度（防呆） */
    private static final int MAX_PACKET_LENGTH = 64 * 1024;

    private CMPPCodecKit() {
    }

    /**
     * 解码（拆包 + 解析）：从 Mina 累积缓冲中消费全部完整 CMPP 帧并逐帧回调。
     * 半包回退（mark/reset）等待累积；包长非法关闭会话；未知命令字丢弃不中断。
     *
     * @param session  会话（非法包长时用于关闭），可为 null（跳过关闭）
     * @param in       Mina 累积缓冲（与 ProtocolDecoder.decode 入参同一引用）
     * @param consumer 每解出一帧回调一次（如 ProtocolDecoderOutput::write）
     */
    public static void decodeFrames(IoSession session, IoBuffer in, CMPPFrameConsumer consumer) {
        while (true) {
            if (in.remaining() < HEADER_LENGTH) {
                return;
            }

            in.mark();
            int totalLength = in.getInt();

            if (totalLength < HEADER_LENGTH || totalLength > MAX_PACKET_LENGTH) {
                logger.warn("Invalid CMPP packet length: {}, closing session", totalLength);
                if (session != null) {
                    session.closeNow();
                }
                return;
            }

            if (in.remaining() < totalLength - 4) {
                in.reset();
                return;
            }

            int commandId = in.getInt();
            int sequenceId = in.getInt();

            try {
                CMPPMessage msg = parseFrame(commandId, sequenceId, totalLength,
                        BufferBridge.toNetty(in, totalLength - HEADER_LENGTH));
                consumer.accept(msg);
            } catch (Exception e) {
                logger.warn("Failed to decode CMPP packet, commandId={}, seq={}: {}",
                        String.format("0x%08X", commandId), sequenceId, e.getMessage());
            }
        }
    }

    /**
     * 解析单个 CMPP 帧的消息体（供 Mina decodeFrames 与 Netty CMPPDecoder 共用）。
     *
     * @param commandId   命令字
     * @param sequenceId  流水号
     * @param totalLength 消息总长度
     * @param body        消息体（仅 body，不含 12 字节头）
     */
    public static CMPPMessage parseFrame(int commandId, int sequenceId, int totalLength, ByteBuf body) {
        switch (commandId) {
            case CMPPCommand.CMPP_CONNECT:
                return parseConnect(body, totalLength, sequenceId);
            case CMPPCommand.CMPP_CONNECT_RESP:
                return parseConnectResp(body, totalLength, sequenceId);
            case CMPPCommand.CMPP_SUBMIT:
                return parseSubmit(body, totalLength, sequenceId);
            case CMPPCommand.CMPP_SUBMIT_RESP:
                return parseSubmitResp(body, totalLength, sequenceId);
            case CMPPCommand.CMPP_DELIVER:
                return parseDeliver(body, totalLength, sequenceId);
            case CMPPCommand.CMPP_DELIVER_RESP:
                return parseDeliverResp(body, totalLength, sequenceId);
            case CMPPCommand.CMPP_ACTIVE_TEST:
                return parseActiveTest(body, totalLength, sequenceId);
            case CMPPCommand.CMPP_ACTIVE_TEST_RESP:
                return parseActiveTestResp(body, totalLength, sequenceId);
            default:
                throw new IllegalArgumentException("Unknown CMPP commandId: 0x" + Integer.toHexString(commandId));
        }
    }

    /**
     * 编码（组包）：CMPPMessage → 完整网络包 IoBuffer（12 字节头 + body）。
     */
    public static IoBuffer encodeFrame(CMPPMessage msg) {
        ByteBuf bodyBuf = Unpooled.buffer();
        msg.writeBody(bodyBuf);

        int totalLength = HEADER_LENGTH + bodyBuf.readableBytes();
        IoBuffer io = IoBuffer.allocate(totalLength);
        io.putInt(totalLength);
        io.putInt(msg.getCommandId());
        io.putInt(msg.getSequenceId());
        BufferBridge.writeTo(io, bodyBuf);
        io.flip();
        return io;
    }

    // ==================== 各消息体解析 ====================

    private static CMPPConnect parseConnect(ByteBuf in, int totalLength, int sequenceId) {
        CMPPConnect msg = new CMPPConnect();
        msg.setHeader(new CMPPHeader(CMPPCommand.CMPP_CONNECT, sequenceId));
        msg.getHeader().setTotalLength(totalLength);

        byte[] sourceAddrBytes = new byte[6];
        in.readBytes(sourceAddrBytes);
        msg.setSourceAddr(CMPPCodecUtil.readCString(sourceAddrBytes));

        byte[] auth = new byte[16];
        in.readBytes(auth);
        msg.setAuthenticatorSource(auth);

        msg.setVersion(in.readByte());
        msg.setTimestamp(in.readInt());
        return msg;
    }

    private static CMPPConnectResp parseConnectResp(ByteBuf in, int totalLength, int sequenceId) {
        CMPPConnectResp msg = new CMPPConnectResp();
        msg.setHeader(new CMPPHeader(CMPPCommand.CMPP_CONNECT_RESP, sequenceId));
        msg.getHeader().setTotalLength(totalLength);

        msg.setStatus(in.readByte());
        byte[] auth = new byte[16];
        in.readBytes(auth);
        msg.setAuthenticatorISMG(auth);
        msg.setVersion(in.readByte());
        return msg;
    }

    private static CMPPSubmit parseSubmit(ByteBuf in, int totalLength, int sequenceId) {
        CMPPSubmit msg = new CMPPSubmit();
        msg.setHeader(new CMPPHeader(CMPPCommand.CMPP_SUBMIT, sequenceId));
        msg.getHeader().setTotalLength(totalLength);

        msg.setMsgId(in.readLong());
        msg.setPkTotal(in.readUnsignedByte());
        msg.setPkNumber(in.readUnsignedByte());
        msg.setRegisteredDelivery(in.readUnsignedByte());
        msg.setMsgLevel(in.readUnsignedByte());

        msg.setServiceId(CMPPCodecUtil.readCString(in, 10));
        msg.setFeeUserType(in.readUnsignedByte());
        msg.setFeeTerminalId(CMPPCodecUtil.readCString(in, 21));

        msg.setTpPid(in.readUnsignedByte());
        msg.setTpUdhi(in.readUnsignedByte());
        msg.setMsgFmt(in.readUnsignedByte());

        msg.setMsgSrc(CMPPCodecUtil.readCString(in, 6));
        msg.setFeeType(CMPPCodecUtil.readCString(in, 2));
        msg.setFeeCode(CMPPCodecUtil.readCString(in, 6));
        msg.setValIdTime(CMPPCodecUtil.readCString(in, 17));
        msg.setAtTime(CMPPCodecUtil.readCString(in, 17));
        msg.setSrcId(CMPPCodecUtil.readCString(in, 21));

        int destUsrTl = in.readUnsignedByte();
        msg.setDestUsrTl(destUsrTl);
        String[] destIds = new String[destUsrTl];
        for (int i = 0; i < destUsrTl; i++) {
            destIds[i] = CMPPCodecUtil.readCString(in, 21);
        }
        msg.setDestTerminalId(destIds);

        int msgLength = in.readUnsignedByte();
        msg.setMsgLength(msgLength);
        byte[] content = new byte[msgLength];
        in.readBytes(content);
        msg.setMsgContent(content);
        return msg;
    }

    private static CMPPSubmitResp parseSubmitResp(ByteBuf in, int totalLength, int sequenceId) {
        CMPPSubmitResp msg = new CMPPSubmitResp();
        msg.setHeader(new CMPPHeader(CMPPCommand.CMPP_SUBMIT_RESP, sequenceId));
        msg.getHeader().setTotalLength(totalLength);
        msg.setMsgId(in.readLong());
        msg.setResult(in.readUnsignedByte());
        return msg;
    }

    private static CMPPDeliver parseDeliver(ByteBuf in, int totalLength, int sequenceId) {
        CMPPDeliver msg = new CMPPDeliver();
        msg.setHeader(new CMPPHeader(CMPPCommand.CMPP_DELIVER, sequenceId));
        msg.getHeader().setTotalLength(totalLength);

        msg.setMsgId(in.readLong());
        msg.setDestId(CMPPCodecUtil.readCString(in, 21));
        msg.setServiceId(CMPPCodecUtil.readCString(in, 10));
        msg.setTpPid(in.readUnsignedByte());
        msg.setTpUdhi(in.readUnsignedByte());
        msg.setMsgFmt(in.readUnsignedByte());
        msg.setSrcTerminalId(CMPPCodecUtil.readCString(in, 21));
        msg.setRegisteredDelivery(in.readUnsignedByte());

        int msgLength = in.readUnsignedByte();
        msg.setMsgLength(msgLength);
        byte[] content = new byte[msgLength];
        in.readBytes(content);
        msg.setMsgContent(content);

        byte[] reserve = new byte[8];
        in.readBytes(reserve);
        msg.setReserve(reserve);

        if (msg.getRegisteredDelivery() == 1) {
            parseDeliverReport(msg, content);
        }
        return msg;
    }

    private static CMPPDeliverResp parseDeliverResp(ByteBuf in, int totalLength, int sequenceId) {
        CMPPDeliverResp msg = new CMPPDeliverResp();
        msg.setHeader(new CMPPHeader(CMPPCommand.CMPP_DELIVER_RESP, sequenceId));
        msg.getHeader().setTotalLength(totalLength);
        msg.setMsgId(in.readLong());
        msg.setResult(in.readInt());
        return msg;
    }

    private static CMPPActiveTest parseActiveTest(ByteBuf in, int totalLength, int sequenceId) {
        CMPPActiveTest msg = new CMPPActiveTest();
        msg.setHeader(new CMPPHeader(CMPPCommand.CMPP_ACTIVE_TEST, sequenceId));
        msg.getHeader().setTotalLength(totalLength);
        return msg;
    }

    private static CMPPActiveTestResp parseActiveTestResp(ByteBuf in, int totalLength, int sequenceId) {
        CMPPActiveTestResp msg = new CMPPActiveTestResp();
        msg.setHeader(new CMPPHeader(CMPPCommand.CMPP_ACTIVE_TEST_RESP, sequenceId));
        msg.getHeader().setTotalLength(totalLength);
        msg.setReserved(in.readByte());
        return msg;
    }

    private static void parseDeliverReport(CMPPDeliver msg, byte[] content) {
        // 状态报告格式：Msg_Id(8) + Stat(7) + Submit_Time(10) + Done_Time(10)
        //              + Dest_Terminal_Id(21) + Smsc_Sequence(4) = 60 字节
        ByteBuf buf = Unpooled.wrappedBuffer(content);
        try {
            msg.setReportMsgId(buf.readLong());
            msg.setStat(CMPPCodecUtil.readCString(buf, 7));
            msg.setSubmitTime(CMPPCodecUtil.readCString(buf, 10));
            msg.setDoneTime(CMPPCodecUtil.readCString(buf, 10));
            msg.setDestTerminalId(CMPPCodecUtil.readCString(buf, 21));
            msg.setSmscSequence(buf.readInt());
        } finally {
            buf.release();
        }
    }

    /** 解码帧回调 */
    @FunctionalInterface
    public interface CMPPFrameConsumer {
        void accept(CMPPMessage message);
    }
}
