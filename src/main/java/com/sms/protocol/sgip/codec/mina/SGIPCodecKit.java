package com.sms.protocol.sgip.codec.mina;

import com.sms.protocol.sgip.codec.SGIPDecoder;
import com.sms.protocol.sgip.constant.SGIPCommand;
import com.sms.protocol.sgip.message.*;
import com.sms.protocol.utils.BufferBridge;
import com.sms.protocol.utils.SGIPCodecUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SGIP 编解码工具类（自研实现核心逻辑，与 SMGP 的 {@code SmgpCodecKit} 同模式）。
 * <p>
 * 拆包/解析/组包核心逻辑集中在此，供两处复用：
 * <ul>
 *   <li>Mina 生产侧：decoder/encoder 内 if/else 切换时调用 {@link #decodeFrames} / {@link #encodeFrame}，
 *       实现报文级动态切换（配置变更后下一个包即生效，无需关闭连接）；</li>
 *   <li>Netty 侧：{@link SGIPDecoder} 复用 {@link #parseFrame}，解析逻辑只有一份。</li>
 * </ul>
 */
public final class SGIPCodecKit {

    private static final Logger logger = LoggerFactory.getLogger(SGIPCodecKit.class);

    /** SGIP 消息头固定长度：20 字节 */
    private static final int HEADER_LENGTH = 20;

    /** 单个 SGIP 数据包的最大长度（防呆） */
    private static final int MAX_PACKET_LENGTH = 64 * 1024;

    private SGIPCodecKit() {
    }

    /**
     * 解码（拆包 + 解析）：从 Mina 累积缓冲中消费全部完整 SGIP 帧并逐帧回调。
     * 半包回退（mark/reset）等待累积；包长非法关闭会话；未知命令字丢弃不中断。
     *
     * @param session  会话（非法包长时用于关闭），可为 null（跳过关闭）
     * @param in       Mina 累积缓冲（与 ProtocolDecoder.decode 入参同一引用）
     * @param consumer 每解出一帧回调一次（如 ProtocolDecoderOutput::write）
     */
    public static void decodeFrames(IoSession session, IoBuffer in, SGIPFrameConsumer consumer) {
        while (true) {
            if (in.remaining() < HEADER_LENGTH) {
                return;
            }

            in.mark();
            int totalLength = in.getInt();

            if (totalLength < HEADER_LENGTH || totalLength > MAX_PACKET_LENGTH) {
                logger.warn("Invalid SGIP packet length: {}, closing session", totalLength);
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
            SGIPHeader header = new SGIPHeader();
            header.setTotalLength(totalLength);
            header.setCommandId(commandId);
            header.setSourceNodeId(in.getUnsignedInt());
            header.setDestNodeId(in.getUnsignedInt());
            header.setSequenceNumber(in.getUnsignedInt());

            try {
                SGIPMessage msg = parseFrame(header, BufferBridge.toNetty(in, totalLength - HEADER_LENGTH));
                consumer.accept(msg);
            } catch (Exception e) {
                logger.warn("Failed to decode SGIP packet, commandId={}, seq={}: {}",
                        String.format("0x%08X", commandId), header.getSequenceNumber(), e.getMessage());
            }
        }
    }

    /**
     * 解析单个 SGIP 帧的消息体（供 Mina decodeFrames 与 Netty SGIPDecoder 共用）。
     *
     * @param header 已解析的消息头（含命令字与三段序列号）
     * @param body   消息体（仅 body，不含 20 字节头）
     */
    public static SGIPMessage parseFrame(SGIPHeader header, ByteBuf body) {
        int cmd = header.getCommandId();
        switch (cmd) {
            case SGIPCommand.SGIP_BIND:
                return parseBind(header, body);
            case SGIPCommand.SGIP_BIND_RESP:
                return parseBindResp(header, body);
            case SGIPCommand.SGIP_SUBMIT:
                return parseSubmit(header, body);
            case SGIPCommand.SGIP_SUBMIT_RESP:
                return parseSimpleResp(header, body, new SGIPSubmitResp());
            case SGIPCommand.SGIP_DELIVER:
                return parseDeliver(header, body);
            case SGIPCommand.SGIP_DELIVER_RESP:
                return parseSimpleResp(header, body, new SGIPDeliverResp());
            case SGIPCommand.SGIP_REPORT:
                return parseReport(header, body);
            case SGIPCommand.SGIP_REPORT_RESP:
                return parseSimpleResp(header, body, new SGIPReportResp());
            case SGIPCommand.SGIP_ACTIVE_TEST:
                SGIPActiveTest activeTest = new SGIPActiveTest();
                activeTest.setHeader(header);
                return activeTest;
            case SGIPCommand.SGIP_ACTIVE_TEST_RESP:
                SGIPActiveTestResp activeTestResp = new SGIPActiveTestResp();
                activeTestResp.setHeader(header);
                return activeTestResp;
            case SGIPCommand.SGIP_TERMINATE:
                SGIPTerminate terminate = new SGIPTerminate();
                terminate.setHeader(header);
                return terminate;
            case SGIPCommand.SGIP_TERMINATE_RESP:
                SGIPTerminateResp terminateResp = new SGIPTerminateResp();
                terminateResp.setHeader(header);
                return terminateResp;
            default:
                throw new IllegalArgumentException("未知命令类型: 0x" + Integer.toHexString(cmd));
        }
    }

    /**
     * 编码（组包）：SGIPMessage → 完整网络包 IoBuffer（20 字节头 + body）。
     */
    public static IoBuffer encodeFrame(SGIPMessage msg) {
        ByteBuf out = Unpooled.buffer();
        SGIPHeader header = msg.getHeader();

        // 1. 预留消息头位置（20字节）
        int headerIndex = out.writerIndex();
        out.writeZero(HEADER_LENGTH);

        // 2. 写入消息体
        msg.writeBody(out);

        // 3. 计算总长度，回填消息头
        int totalLength = out.writerIndex() - headerIndex;
        header.setTotalLength(totalLength);

        // 4. 写入完整消息头
        out.setInt(headerIndex, totalLength);
        out.setInt(headerIndex + 4, header.getCommandId());
        out.writerIndex(headerIndex + 8);
        SGIPCodecUtil.writeSequenceId(out,
                header.getSourceNodeId(),
                header.getDestNodeId(),
                header.getSequenceNumber());

        out.writerIndex(headerIndex + totalLength);
        return BufferBridge.toMina(out);
    }

    // ==================== 各消息体解析 ====================

    private static SGIPBind parseBind(SGIPHeader header, ByteBuf in) {
        SGIPBind bind = new SGIPBind();
        bind.setHeader(header);
        bind.setLoginType(in.readUnsignedByte());
        bind.setName(SGIPCodecUtil.readCString(in, 16));
        bind.setPassword(SGIPCodecUtil.readCString(in, 16));
        byte[] reserve = new byte[8];
        in.readBytes(reserve);
        bind.setReserve(reserve);
        return bind;
    }

    private static SGIPBindResp parseBindResp(SGIPHeader header, ByteBuf in) {
        SGIPBindResp resp = new SGIPBindResp();
        resp.setHeader(header);
        resp.setResult(in.readUnsignedByte());
        byte[] reserve = new byte[8];
        in.readBytes(reserve);
        resp.setReserve(reserve);
        return resp;
    }

    private static SGIPSubmit parseSubmit(SGIPHeader header, ByteBuf in) {
        SGIPSubmit submit = new SGIPSubmit();
        submit.setHeader(header);

        submit.setSpNumber(SGIPCodecUtil.readCString(in, 21));
        submit.setChargeNumber(SGIPCodecUtil.readCString(in, 21));

        int userCount = in.readUnsignedByte();
        submit.setUserCount(userCount);
        String[] numbers = new String[userCount];
        for (int i = 0; i < userCount; i++) {
            numbers[i] = SGIPCodecUtil.readCString(in, 21);
        }
        submit.setUserNumber(numbers);

        submit.setCorpId(SGIPCodecUtil.readCString(in, 5));
        submit.setServiceType(SGIPCodecUtil.readCString(in, 10));

        submit.setFeeType(in.readUnsignedByte());
        submit.setFeeValue(SGIPCodecUtil.readCString(in, 6));
        submit.setGivenValue(SGIPCodecUtil.readCString(in, 6));
        submit.setAgentFlag(in.readUnsignedByte());
        submit.setMorelatetoMTFlag(in.readUnsignedByte());
        submit.setPriority(in.readUnsignedByte());

        submit.setExpireTime(SGIPCodecUtil.readCString(in, 16));
        submit.setScheduleTime(SGIPCodecUtil.readCString(in, 16));

        submit.setReportFlag(in.readUnsignedByte());
        submit.setTpPid(in.readUnsignedByte());
        submit.setTpUdhi(in.readUnsignedByte());
        submit.setMessageCoding(in.readUnsignedByte());
        submit.setMessageType(in.readUnsignedByte());

        int msgLen = in.readInt();
        submit.setMessageLength(msgLen);
        byte[] content = new byte[msgLen];
        in.readBytes(content);
        submit.setMessageContent(content);

        byte[] reserve = new byte[8];
        in.readBytes(reserve);
        submit.setReserve(reserve);
        return submit;
    }

    private static SGIPDeliver parseDeliver(SGIPHeader header, ByteBuf in) {
        SGIPDeliver deliver = new SGIPDeliver();
        deliver.setHeader(header);
        deliver.setUserNumber(SGIPCodecUtil.readCString(in, 21));
        deliver.setSpNumber(SGIPCodecUtil.readCString(in, 21));
        deliver.setTpPid(in.readUnsignedByte());
        deliver.setTpUdhi(in.readUnsignedByte());
        deliver.setMsgFormat(in.readUnsignedByte());
        deliver.setSrcTerminalId(SGIPCodecUtil.readCString(in, 21));

        int msgLen = in.readUnsignedByte();
        deliver.setMsgLength(msgLen);
        byte[] content = new byte[msgLen];
        in.readBytes(content);
        deliver.setMsgContent(content);

        byte[] reserve = new byte[8];
        in.readBytes(reserve);
        deliver.setReserve(reserve);
        return deliver;
    }

    private static SGIPReport parseReport(SGIPHeader header, ByteBuf in) {
        SGIPReport report = new SGIPReport();
        report.setHeader(header);
        long[] seq = SGIPCodecUtil.readSequenceId(in);
        report.setSubmitSrcNodeId(seq[0]);
        report.setSubmitDestNodeId(seq[1]);
        report.setSubmitSequenceNumber(seq[2]);
        report.setReportType(in.readUnsignedByte());
        report.setUserNumber(SGIPCodecUtil.readCString(in, 21));
        report.setState(in.readUnsignedByte());
        report.setErrorCode(in.readUnsignedByte());
        byte[] reserve = new byte[8];
        in.readBytes(reserve);
        report.setReserve(reserve);
        return report;
    }

    /**
     * 通用单字节结果+8字节保留的响应解析
     */
    private static <T extends SGIPMessage> T parseSimpleResp(SGIPHeader header, ByteBuf in, T resp) {
        try {
            resp.getClass().getMethod("setHeader", SGIPHeader.class).invoke(resp, header);
            resp.getClass().getMethod("setResult", int.class).invoke(resp, in.readUnsignedByte());
            byte[] reserve = new byte[8];
            in.readBytes(reserve);
            resp.getClass().getMethod("setReserve", byte[].class).invoke(resp, (Object) reserve);
            return resp;
        } catch (Exception e) {
            throw new RuntimeException("响应解析失败", e);
        }
    }

    /** 解码帧回调 */
    @FunctionalInterface
    public interface SGIPFrameConsumer {
        void accept(SGIPMessage message);
    }
}
