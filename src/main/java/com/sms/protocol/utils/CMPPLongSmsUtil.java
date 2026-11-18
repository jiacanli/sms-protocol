package com.sms.protocol.utils;

import com.sms.protocol.cmpp20.message.CMPPSubmit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CMPP 2.0 长短信拆分工具类（仅依赖字符串入参）
 */
public final class CMPPLongSmsUtil {

    private CMPPLongSmsUtil() {}

    /** 参考号生成器（0~255 循环） */
    private static final AtomicInteger REFERENCE_GENERATOR = new AtomicInteger(0);

    private static byte nextReference() {
        return (byte) (REFERENCE_GENERATOR.getAndIncrement() & 0xFF);
    }

    // ==================================================================
    //                    拆分入口（String → List<CMPPSubmit>）
    // ==================================================================

    /**
     * 拆分长短信为多条 CMPPSubmit（默认 6 字节 UDH）
     *
     * @param content 待发送的完整短文明文
     * @param msgFmt  信息格式：0=ASCII，8=UCS2，15=GBK
     * @return 分片列表，只填充了分片相关字段：
     *         msgFmt、tpUdhi、pkTotal、pkNumber、msgContent、msgLength
     *         其余业务字段（msgSrc、srcId、destTerminalId 等）需业务层自行设置
     */
    public static List<CMPPSubmit> split(String content, int msgFmt) throws IOException {
        return split(content, msgFmt, false);
    }

    /**
     * 拆分长短信为多条 CMPPSubmit
     *
     * @param content  待发送的完整短文明文
     * @param msgFmt   信息格式：0=ASCII，8=UCS2，15=GBK
     * @param use7Byte 是否使用 7 字节 UDH（参考号 0~65535）
     * @return 分片列表
     */
    public static List<CMPPSubmit> split(String content,
                                         int msgFmt,
                                         boolean use7Byte) throws IOException {

        Charset charset = resolveCharset(msgFmt);
        List<CMPPSubmit> result = new ArrayList<>();

        // ---- 空内容：单条普通短信 ----
        if (content == null || content.isEmpty()) {
            result.add(buildPiece(msgFmt, 0, 1, 1, new byte[0]));
            return result;
        }

        // ---- 计算每片正文最大字节数 ----
        int udhLen = use7Byte
                ? CMPPLongSmsConstants.UDH_HEADER_LENGTH_7
                : CMPPLongSmsConstants.UDH_HEADER_LENGTH_6;
        int maxPayloadBytes = CMPPLongSmsConstants.MAX_SINGLE_MSG_LENGTH - udhLen;

        // ---- 按字符边界切分 ----
        List<String> segments = splitByCharBoundary(content, maxPayloadBytes, charset);
        int total = segments.size();

        if (total > 255 && !use7Byte) {
            throw new IllegalArgumentException(
                    "6 字节 UDH 最多支持 255 个分片，当前需要 " + total + " 片，请使用 7 字节 UDH");
        }
        if (total > 65535) {
            throw new IllegalArgumentException("分片数量过多: " + total);
        }

        // ---- 单条：不附加 UDH ----
        if (total == 1) {
            byte[] payload = segments.get(0).getBytes(charset);
            result.add(buildPiece(msgFmt, 0, 1, 1, payload));
            return result;
        }

        // ---- 多条：生成同一参考号，逐片构建 ----
        byte reference = nextReference();
        for (int i = 0; i < total; i++) {
            byte[] udh     = buildUdh(use7Byte, reference, (byte) total, (byte) (i + 1));
            byte[] payload = segments.get(i).getBytes(charset);
            byte[] msgContent = concat(udh, payload);

            result.add(buildPiece(msgFmt, 1, total, i + 1, msgContent));
        }
        return result;
    }

    /**
     * 构建单个分片的 CMPPSubmit（只填充分片相关字段）
     */
    private static CMPPSubmit buildPiece(int msgFmt,
                                         int tpUdhi,
                                         int pkTotal,
                                         int pkNumber,
                                         byte[] msgContent) {
        CMPPSubmit piece = new CMPPSubmit();
        piece.setMsgFmt(msgFmt);
        piece.setTpUdhi(tpUdhi);
        piece.setPkTotal(pkTotal);
        piece.setPkNumber(pkNumber);
        piece.setMsgContent(msgContent);
        piece.setMsgLength(msgContent.length);
        return piece;
    }

    // ==================================================================
    //                          内部工具
    // ==================================================================

    private static Charset resolveCharset(int msgFmt) {
        switch (msgFmt) {
            case CMPPLongSmsConstants.MSG_FMT_UCS2:
                return Charset.forName(CMPPLongSmsConstants.CHARSET_UCS2);
            case CMPPLongSmsConstants.MSG_FMT_GBK:
                return Charset.forName(CMPPLongSmsConstants.CHARSET_GBK);
            case CMPPLongSmsConstants.MSG_FMT_ASCII:
            default:
                return Charset.forName("US-ASCII");
        }
    }

    private static byte[] buildUdh(boolean use7Byte, byte reference, byte total, byte seq) {
        if (use7Byte) {
            return new byte[]{
                    0x06, 0x08, 0x04,
                    (byte) ((reference >> 8) & 0xFF),
                    (byte) (reference & 0xFF),
                    total, seq
            };
        } else {
            return new byte[]{
                    0x05, 0x00, 0x03,
                    reference, total, seq
            };
        }
    }

    private static List<String> splitByCharBoundary(String content,
                                                    int maxBytes,
                                                    Charset charset) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            // 增补字符（代理对）
            if (Character.isHighSurrogate(c)
                    && i + 1 < content.length()
                    && Character.isLowSurrogate(content.charAt(i + 1))) {
                String pair = content.substring(i, i + 2);
                byte[] pairBytes = pair.getBytes(charset);
                if (currentBytes + pairBytes.length > maxBytes && current.length() > 0) {
                    segments.add(current.toString());
                    current.setLength(0);
                    currentBytes = 0;
                }
                current.append(pair);
                currentBytes += pairBytes.length;
                i++;
            } else {
                byte[] charBytes = String.valueOf(c).getBytes(charset);
                if (currentBytes + charBytes.length > maxBytes && current.length() > 0) {
                    segments.add(current.toString());
                    current.setLength(0);
                    currentBytes = 0;
                }
                current.append(c);
                currentBytes += charBytes.length;
            }
        }
        if (current.length() > 0) {
            segments.add(current.toString());
        }
        return segments;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(a.length + b.length);
        try {
            baos.write(a);
            baos.write(b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    // ==================================================================
    //                    解码 / 合并（接收侧）
    // ==================================================================

    public static class Fragment {
        private final int reference;
        private final int total;
        private final int seq;
        private final byte[] payload;

        public Fragment(int reference, int total, int seq, byte[] payload) {
            this.reference = reference;
            this.total = total;
            this.seq = seq;
            this.payload = payload;
        }
        public int getReference()  { return reference; }
        public int getTotal()      { return total; }
        public int getSeq()        { return seq; }
        public byte[] getPayload() { return payload; }
    }

    /**
     * 解析分片 CMPPSubmit 的 UDH 头
     */
    public static Fragment parseUdh(CMPPSubmit submit) {
        if (submit.getTpUdhi() != 1) {
            throw new IllegalArgumentException("TP_udhi != 1，不是长短信分片");
        }
        byte[] content = submit.getMsgContent();
        if (content == null || content.length < 6) {
            throw new IllegalArgumentException("Msg_Content 过短，无法解析 UDH");
        }

        int udhLength = content[0] & 0xFF;
        int iei       = content[1] & 0xFF;
        int iel       = content[2] & 0xFF;

        if (iei != CMPPLongSmsConstants.IEI_CONCATENATED_8BIT_REF) {
            throw new IllegalArgumentException("非拼接短信 UDH，IEI=" + iei);
        }

        int reference, total, seq;
        if (iel == 0x03 && udhLength == 0x05) {
            reference = content[3] & 0xFF;
            total     = content[4] & 0xFF;
            seq       = content[5] & 0xFF;
        } else if (iel == 0x04 && udhLength == 0x06) {
            reference = ((content[3] & 0xFF) << 8) | (content[4] & 0xFF);
            total     = content[5] & 0xFF;
            seq       = content[6] & 0xFF;
        } else {
            throw new IllegalArgumentException(
                    "未知 UDH 格式: udhLength=" + udhLength + ", iel=" + iel);
        }

        int headerTotalLen = 1 + udhLength;
        byte[] payload = new byte[content.length - headerTotalLen];
        System.arraycopy(content, headerTotalLen, payload, 0, payload.length);

        return new Fragment(reference, total, seq, payload);
    }

    public static String decodePayload(byte[] payload, int msgFmt) {
        return new String(payload, resolveCharset(msgFmt));
    }
}
