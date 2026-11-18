package com.sms.protocol.smgp.message;

import lombok.Data;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SMGP 状态报告（收条）。
 * <p>
 * SMGP 3.0 的状态报告不占用独立命令字，而是由 SMGW 通过 Deliver 消息携带：
 * 当 Deliver.IsReport = 1 时，MsgContent 为状态报告文本，格式如下（SMPP 风格，各字段以空格分隔）：
 * <pre>
 * id:01006101161700012345 sub:001 dlvrd:001 submit date:010331200000 done date:010331200000 stat:DELIVRD err:000 text:...
 * </pre>
 * 各字段含义：
 * <ul>
 *   <li>id          —— 原 Submit 返回的短消息流水号（MsgID）</li>
 *   <li>sub         —— 提交条数</li>
 *   <li>dlvrd       —— 送达条数</li>
 *   <li>submit date —— 短消息提交时间（yymmddhhmm）</li>
 *   <li>done date   —— 短消息下发时间（yymmddhhmm）</li>
 *   <li>stat        —— 状态（DELIVRD/EXPIRED/DELETED/UNDELIV/ACCEPTD/UNKNOWN 等）</li>
 *   <li>err         —— 错误码</li>
 *   <li>text        —— 短消息内容（可能为空）</li>
 * </ul>
 */
@Data
public class SmgpReport {

    private String msgId;         // id
    private String sub;           // sub
    private String dlvrd;         // dlvrd
    private String submitDate;    // submit date
    private String doneDate;      // done date
    private String stat;          // stat
    private String err;           // err
    private String txt;           // text

    /** 状态报告原始字节（保留供排查/落库，不参与 toString） */
    private byte[] rawBytes;

    private static final Charset GBK = Charset.forName("GBK");

    /** 挡板/信雅达 VISMG 系列网关状态报告采用 GB18030 编码 */
    private static final Charset GB18030 = Charset.forName("GB18030");

    private static final Pattern REPORT_PATTERN = Pattern.compile(
            "id:(\\S*)\\s+sub:(\\S*)\\s+dlvrd:(\\S*)\\s+submit\\s+date:(\\S*)\\s+done\\s+date:(\\S*)\\s+stat:(\\S*)\\s+err:(\\S*)(?:\\s+text:(.*))?",
            Pattern.CASE_INSENSITIVE);

    /**
     * 从字节内容解析状态报告（Deliver.IsReport=1 时的 MsgContent）：
     * <ol>
     *   <li>优先按标准 SMPP 文本格式解析（按 msgFormat 指定编码解码 + 正则）</li>
     *   <li>不匹配时尝试挡板/信雅达 VISMG 二进制定长格式</li>
     *   <li>均未识别则保留解码原文与原始字节供排查</li>
     * </ol>
     */
    public static SmgpReport parse(byte[] content, int msgFormat) {
        if (content == null) return new SmgpReport();

        // 1. 标准 SMPP 文本格式（正则匹配成功即填充 stat）
        SmgpReport r = parse(decodeByFormat(content, msgFormat));
        if (r.stat != null && !r.stat.isEmpty()) {
            r.rawBytes = content;
            return r;
        }

        // 2. 挡板二进制定长格式（真实字节布局，不依赖文本解码）
        SmgpReport bin = parseBinaryReport(content);
        if (bin != null) {
            bin.rawBytes = content;
            return bin;
        }

        // 3. 均未识别：保留主编码解码原文 + 原始字节，供排查
        r.rawBytes = content;
        return r;
    }

    /**
     * 解析挡板二进制定长状态报告（固定 122 字节，信雅达 VISMG 风格）：
     * <pre>
     * "id:"(3) + 00 00(2) + long msgId(8, 网络序大端) + 00×8 + 00×10 + 00×13
     * + doneTime(10, GB18030) + 00×11 + submitTime(10, GB18030)
     * + " stat:DELIVRD"(13, GB18030) + 00×34
     * </pre>
     * 识别失败返回 null。
     */
    private static SmgpReport parseBinaryReport(byte[] content) {
        if (content == null || content.length < 122) return null;
        if (content[0] != 'i' || content[1] != 'd' || content[2] != ':') return null;
        if (content[3] != 0 || content[4] != 0) return null;

        try {
            SmgpReport r = new SmgpReport();

            // msgId：offset 5-12，8 字节 long（网络序大端）→ 十进制字符串（与 SubmitResp 兼容处理一致）
            long msgId = 0;
            for (int i = 5; i < 13; i++) {
                msgId = (msgId << 8) | (content[i] & 0xFF);
            }
            r.msgId = String.valueOf(msgId);

            // 时间字段：offset 44-53 为 doneTime、65-74 为 submitTime（挡板为毫秒时间戳截断前 10 位，非 YYMMDDHHMM）
            r.doneDate = trimZero(new String(content, 44, 10, GB18030));
            r.submitDate = trimZero(new String(content, 65, 10, GB18030));

            // stat：offset 75-87，" stat:DELIVRD"（带前导空格）
            String statBlock = trimZero(new String(content, 75, 13, GB18030));
            int idx = statBlock.indexOf("stat:");
            r.stat = (idx >= 0 ? statBlock.substring(idx + 5) : statBlock).trim();
            if (r.stat.isEmpty()) return null;

            // 挡板无 sub/dlvrd/err/text 字段（对应位置为 0x00 填充），保持 null
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimZero(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) != 0) i++;
        return s.substring(0, i);
    }

    /** 从字节内容解析状态报告（默认按 ASCII，兼容旧调用） */
    public static SmgpReport parse(byte[] content) {
        return parse(content, 0);
    }

    private static String decodeByFormat(byte[] content, int msgFormat) {
        switch (msgFormat) {
            case 8:  return new String(content, StandardCharsets.UTF_16BE);
            case 15: return new String(content, GBK);
            default: return new String(content, StandardCharsets.US_ASCII);
        }
    }

    /** 从文本解析状态报告 */
    public static SmgpReport parse(String text) {
        SmgpReport r = new SmgpReport();
        if (text == null || text.isEmpty()) return r;

        Matcher m = REPORT_PATTERN.matcher(text.trim());
        if (m.matches()) {
            r.msgId = m.group(1);
            r.sub = m.group(2);
            r.dlvrd = m.group(3);
            r.submitDate = m.group(4);
            r.doneDate = m.group(5);
            r.stat = m.group(6);
            r.err = m.group(7);
            r.txt = m.group(8);
        } else {
            // 非标准文本（如旧版二进制/未知格式），保留原文到 txt 供排查
            r.txt = text;
        }
        return r;
    }

    /** 编码为标准文本格式（与网关下发格式一致） */
    public byte[] encode() {
        return toString().getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(96);
        sb.append("id:").append(nvl(msgId))
                .append(" sub:").append(nvl(sub, "001"))
                .append(" dlvrd:").append(nvl(dlvrd, "001"))
                .append(" submit date:").append(nvl(submitDate))
                .append(" done date:").append(nvl(doneDate))
                .append(" stat:").append(nvl(stat))
                .append(" err:").append(nvl(err))
                .append(" text:").append(nvl(txt));
        return sb.toString();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String nvl(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
    }

    /* Getter / Setter 可 Lombok */
}
