package com.sms.protocol.smgp.message;
import com.sms.protocol.smgp.SmgpConstants;

import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class SmgpLogin extends SmgpMessage {

    private String clientId;                 // 8
    private byte[] authenticatorClient;      // 16
    private byte loginMode;                  // 1
    private int timestamp;                   // 4
    private byte clientVersion;
    /* ================= 编码 ================= */

    @Override
    public int getBodyLength() {
        // ClientID(8) + AuthenticatorClient(16) + LoginMode(1) + TimeStamp(4) + ClientVersion(1)
        return 30;
    }

    @Override
    public void encodeBody(ByteBuf buf) {
        writeFixedString(buf, clientId, 8);
        buf.writeBytes(authenticatorClient);
        buf.writeByte(loginMode);
        buf.writeInt(timestamp);
        buf.writeByte(clientVersion);
    }

    /* ================= 解码 ================= */

    @Override
    public void decodeBody(ByteBuf buf) {
        clientId = readFixedString(buf, 8);
        authenticatorClient = new byte[16];
        buf.readBytes(authenticatorClient);
        loginMode = buf.readByte();
        timestamp = buf.readInt();
        clientVersion = buf.readByte();
    }

    @Override
    public void initHeader() {
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_LOGIN, SequenceIdGenerator.next());
        this.setHeader(header);
    }

    /* ================= 工厂方法 ================= */

    /**
     * 构造客户端登录包
     */
    public static SmgpLogin build(String clientId, String sharedSecret, byte loginMode) {
        SmgpLogin login = new SmgpLogin();
        login.clientId = clientId;
        login.loginMode = loginMode;
        login.timestamp = buildTimestamp();
        login.authenticatorClient = genAuthenticatorClient(
                clientId, sharedSecret, login.timestamp);
        login.clientVersion = SmgpConstants.PROTOCOL_VERSION;
        return login;
    }

    /* ================= AuthenticatorClient 算法 ================= */

    /**
     * AuthenticatorClient = MD5(ClientId + 7*0x00 + SharedSecret + TimeStamp)
     * TimeStamp 为 4 字节网络序整数（MMDDHHMMSS），见规范 6.2.2。
     */
    public static byte[] genAuthenticatorClient(String clientId,
                                                String secret,
                                                int timestamp) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String auth = clientId+"\0\0\0\0\0\0\0"+secret+timestamp;
            return md5.digest(auth.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("genAuthenticatorClient error", e);
        }
    }

    /* ================= 工具 ================= */

    private static int buildTimestamp() {
        // 格式 MMDDHHMMSS
        LocalDateTime now = LocalDateTime.now();
        String ts = now.format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        return Integer.parseInt(ts);
    }

    protected void writeFixedString(ByteBuf buf, String value, int len) {
        if (value == null) value = "";
        byte[] data = value.getBytes(StandardCharsets.US_ASCII);
        if (data.length >= len) buf.writeBytes(data, 0, len);
        else {
            buf.writeBytes(data);
            for (int i = data.length; i < len; i++) buf.writeByte(0);
        }
    }

    protected String readFixedString(ByteBuf buf, int len) {
        byte[] data = new byte[len];
        buf.readBytes(data);
        int i = 0;
        while (i < data.length && data[i] != 0) i++;
        return new String(data, 0, i, StandardCharsets.US_ASCII);
    }
}


