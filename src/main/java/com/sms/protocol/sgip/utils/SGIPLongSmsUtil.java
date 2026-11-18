package com.sms.protocol.sgip.utils;

import com.sms.protocol.sgip.message.SGIPDeliver;
import com.sms.protocol.sgip.message.SGIPSubmit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SGIP 长短信拆分与合并工具类
 * <p>
 * SGIP 1.2 中长短信通过在消息内容前附加 UDH 头实现，并将 Submit 的 TP_udhi 位置 1。
 * UDH 遵循 GSM 03.40 标准，手机终端根据 UDH 自动重组分片。
 * </p>
 *
 * <p>拆分后只填充以下字段，其余业务字段（spNumber、chargeNumber、userNumber 等）需调用方自行设置：</p>
 * <ul>
 *   <li>messageCoding - 编码格式</li>
 *   <li>messageContent - UDH + 正文（单条无 UDH）</li>
 *   <li>messageLength - messageContent 总长度</li>
 *   <li>tpUdhi - 单条普通短信为 0；多条长短信分片为 1</li>
 * </ul>
 */
public final class SGIPLongSmsUtil {

    private SGIPLongSmsUtil() {}

    /** 参考号生成器（0~255 循环，用于 6 字节 UDH） */
    private static final AtomicInteger REFERENCE_GENERATOR_8BIT = new AtomicInteger(0);
    /** 参考号生成器（0~65535 循环，用于 7 字节 UDH） */
    private static final AtomicInteger REFERENCE_GENERATOR_16BIT = new AtomicInteger(0);

    private static int nextReference8bit() {
        return REFERENCE_GENERATOR_8BIT.getAndIncrement() & 0xFF;
    }

    private static int nextReference16bit() {
        return REFERENCE_GENERATOR_16BIT.getAndIncrement() & 0xFFFF;
    }

    // ==================================================================
    //                    拆分入口（String → List<SGIPSubmit>）
    // ==================================================================

    /**
     * 拆分长短信为多条 SGIPSubmit（默认 6 字节 UDH，8 位参考号）
     *
     * @param content   待发送的完整短文明文
     * @param msgFormat 信息格式：0=ASCII，8=UCS2，15=GBK
     * @return 分片列表，只填充分片相关字段；业务字段需调用方自行设置
     */
    public static List<SGIPSubmit> split(String content, int msgFormat) throws IOException {
        return split(content, msgFormat, false);
    }

    /**
     * 拆分长短信为多条 SGIPSubmit
     *
     * @param content    待发送的完整短文明文
     * @param msgFormat  信息格式：0=ASCII，8=UCS2，15=GBK
     * @param use16bitRef 是否使用 16 位参考号（7 字节 UDH，支持超过 255 片）
     * @return 分片列表
     */
    public static List<SGIPSubmit> split(String content, int msgFormat, boolean use16bitRef)
            throws IOException {

        Charset charset = resolveCharset(msgFormat);
        List<SGIPSubmit> result = new ArrayList<>();

        // 空内容：单条普通短信
        if (content == null || content.isEmpty()) {
            result.add(buildPiece(msgFormat, new byte[0], 0));
            return result;
        }

        // 计算每片正文最大字节数
        int udhLen = use16bitRef
                ? SGIPLongSmsConstants.UDH_HEADER_LENGTH_7
                : SGIPLongSmsConstants.UDH_HEADER_LENGTH_6;
        int maxPayloadBytes = SGIPLongSmsConstants.MAX_SINGLE_MSG_LENGTH - udhLen;

        // 按字符边界切分
        List<String> segments = splitByCharBoundary(content, maxPayloadBytes, charset);
        int total = segments.size();

        if (total > 255 && !use16bitRef) {
            throw new IllegalArgumentException(
                    "6 字节 UDH 最多支持 255 个分片，当前需要 " + total + " 片，请使用 16 位参考号模式");
        }
        if (total > 65535) {
            throw new IllegalArgumentException("分片数量过多: " + total);
        }

        // 单条：不附加 UDH
        if (total == 1) {
            byte[] payload = segments.get(0).getBytes(charset);
            result.add(buildPiece(msgFormat, payload, 0));
            return result;
        }

        // 多条：生成同一参考号，逐片构建（每片都带 UDH，TP_udhi=1）
        int reference = use16bitRef ? nextReference16bit() : nextReference8bit();
        for (int i = 0; i < total; i++) {
            byte[] udh = buildUdh(use16bitRef, reference, total, i + 1);
            byte[] payload = segments.get(i).getBytes(charset);
            byte[] msgContent = concat(udh, payload);
            result.add(buildPiece(msgFormat, msgContent, 1));
        }
        return result;
    }

    // ==================================================================
    //                    接收侧：解析 UDH / 合并分片
    // ==================================================================

    /**
     * 长短信分片信息
     */
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

        public int getReference() { return reference; }
        public int getTotal()     { return total; }
        public int getSeq()       { return seq; }
        public byte[] getPayload() { return payload; }
    }

    /**
     * 解析 SGIPDeliver 中的 UDH 头，提取分片信息
     *
     * @param deliver 上行短信消息
     * @return 分片信息；如果不是长短信分片（无 UDH），返回 null
     */
    public static Fragment parseUdh(SGIPDeliver deliver) {
        byte[] content = deliver.getMsgContent();
        if (content == null || content.length < 6) {
            return null;
        }

        int udhLength = content[0] & 0xFF;
        int iei = content[1] & 0xFF;
        int iel = content[2] & 0xFF;

        // 只处理拼接短信 IEI
        if (iei != SGIPLongSmsConstants.IEI_CONCATENATED_8BIT_REF
                && iei != SGIPLongSmsConstants.IEI_CONCATENATED_16BIT_REF) {
            return null;
        }

        int reference, total, seq;
        if (iei == SGIPLongSmsConstants.IEI_CONCATENATED_8BIT_REF
                && iel == 0x03 && udhLength == 0x05) {
            reference = content[3] & 0xFF;
            total     = content[4] & 0xFF;
            seq       = content[5] & 0xFF;
        } else if (iei == SGIPLongSmsConstants.IEI_CONCATENATED_16BIT_REF
                && iel == 0x04 && udhLength == 0x06) {
            reference = ((content[3] & 0xFF) << 8) | (content[4] & 0xFF);
            total     = content[5] & 0xFF;
            seq       = content[6] & 0xFF;
        } else {
            throw new IllegalArgumentException(
                    "未知 UDH 格式: udhLength=" + udhLength + ", iei=" + iei + ", iel=" + iel);
        }

        int headerTotalLen = 1 + udhLength;
        byte[] payload = new byte[content.length - headerTotalLen];
        System.arraycopy(content, headerTotalLen, payload, 0, payload.length);

        return new Fragment(reference, total, seq, payload);
    }

    /**
     * 将多个分片的 payload 合并为完整字节数组
     *
     * @param fragments 同一参考号的全部分片（需已按 seq 排序）
     * @param msgFormat 编码格式
     * @return 合并后的完整短信内容
     */
    public static String mergeFragments(List<Fragment> fragments, int msgFormat) {
        if (fragments == null || fragments.isEmpty()) {
            return "";
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Fragment f : fragments) {
            baos.write(f.getPayload(), 0, f.getPayload().length);
        }
        return new String(baos.toByteArray(), resolveCharset(msgFormat));
    }

    /**
     * 解码单个分片的 payload 为字符串
     */
    public static String decodePayload(byte[] payload, int msgFormat) {
        return new String(payload, resolveCharset(msgFormat));
    }

    // ==================================================================
    //                          内部工具方法
    // ==================================================================

    /**
     * 构建单个分片的 SGIPSubmit（只填充分片相关字段）
     *
     * @param messageCoding 编码格式
     * @param messageContent 消息内容（单条为纯正文；分片为 UDH + 正文）
     * @param tpUdhi        0=无 UDH（单条普通短信）；1=含 UDH（长短信分片）
     */
    private static SGIPSubmit buildPiece(int messageCoding, byte[] messageContent, int tpUdhi) {
        SGIPSubmit piece = new SGIPSubmit();
        piece.setMessageCoding(messageCoding);
        piece.setMessageContent(messageContent);
        piece.setMessageLength(messageContent.length);
        piece.setTpUdhi(tpUdhi);
        return piece;
    }

    private static Charset resolveCharset(int msgFormat) {
        switch (msgFormat) {
            case SGIPLongSmsConstants.MSG_FMT_UCS2:
                return Charset.forName(SGIPLongSmsConstants.CHARSET_UCS2);
            case SGIPLongSmsConstants.MSG_FMT_GBK:
                return Charset.forName(SGIPLongSmsConstants.CHARSET_GBK);
            case SGIPLongSmsConstants.MSG_FMT_ASCII:
            default:
                return Charset.forName("US-ASCII");
        }
    }

    /**
     * 构建 UDH 头
     *
     * @param use16bitRef 是否使用 16 位参考号
     * @param reference   参考号
     * @param total       总分片数
     * @param seq         当前分片序号（从 1 开始）
     */
    private static byte[] buildUdh(boolean use16bitRef, int reference, int total, int seq) {
        if (use16bitRef) {
            return new byte[]{
                    0x06,                                           // UDH 长度
                    (byte) SGIPLongSmsConstants.IEI_CONCATENATED_16BIT_REF, // IEI
                    0x04,                                           // IE 长度
                    (byte) ((reference >> 8) & 0xFF),
                    (byte) (reference & 0xFF),
                    (byte) total,
                    (byte) seq
            };
        } else {
            return new byte[]{
                    0x05,                                           // UDH 长度
                    (byte) SGIPLongSmsConstants.IEI_CONCATENATED_8BIT_REF,  // IEI
                    0x03,                                           // IE 长度
                    (byte) reference,
                    (byte) total,
                    (byte) seq
            };
        }
    }

    /**
     * 按字符边界切分字符串，确保不会把一个字符（尤其是 Unicode 代理对）拆到两片
     */
    private static List<String> splitByCharBoundary(String content, int maxBytes, Charset charset) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            // 增补字符（代理对）：高代理 + 低代理必须在同一片
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
}
