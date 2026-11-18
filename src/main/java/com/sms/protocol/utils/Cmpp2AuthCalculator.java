package com.sms.protocol.utils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * CMPP 2.0 协议认证码计算工具类
 * 对应协议字段：AuthenticatorSource（SP登录请求认证）、AuthenticatorISMG（网关登录响应认证）
 */
public class Cmpp2AuthCalculator {

    private static final String MD5_ALGORITHM = "MD5";
    // 协议规定使用北京时间（东八区）
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    // 时间戳格式：MMddHHmmss（月日时分秒，共10位数字）
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("MMddHHmmss");

    /**
     * 计算 SP 登录请求中的 AuthenticatorSource（源认证码）
     * 计算公式：MD5(Source_Addr + 9字节0x00 + 共享密钥 + Timestamp)
     *
     * @param sourceAddr   SP企业代码（运营商分配，通常为6位数字字符串）
     * @param sharedSecret 与短信网关线下约定的共享密钥
     * @return 16字节二进制认证码
     * @throws NoSuchAlgorithmException MD5算法不支持时抛出
     */
    public static byte[] calcAuthenticatorSource(String sourceAddr, String sharedSecret,int timestampBytes)
            throws NoSuchAlgorithmException {

        String auth = new StringBuilder(sourceAddr).append("\000\000\000\000\000\000\000\000\0")
                .append(sharedSecret).append(String.valueOf(timestampBytes)).toString();
        MessageDigest md5 = MessageDigest.getInstance(MD5_ALGORITHM);
        return md5.digest(auth.getBytes());
    }


    /**
     * 生成当前时间的4字节大端序时间戳
     * 时间格式 MMddHHmmss 转为数值后，按网络字节序（大端）存储
     */
    public static int generateTimestamp() {
        LocalDateTime now = LocalDateTime.now(BEIJING_ZONE);
        String timestampStr = now.format(TIMESTAMP_FORMATTER);
        return Integer.valueOf(timestampStr);
    }


}

