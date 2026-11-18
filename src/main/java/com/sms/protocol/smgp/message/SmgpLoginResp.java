package com.sms.protocol.smgp.message;

import com.sms.protocol.smgp.SmgpConstants;
import com.sms.protocol.utils.SequenceIdGenerator;
import io.netty.buffer.ByteBuf;
import lombok.Data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Data
public class SmgpLoginResp extends SmgpMessage {

    private int status;                    // 4
    private byte[] authenticatorServer;    // 16
    private byte serverVersion;            // 1，服务器端支持的最高协议版本号

    /* ================= 编码 ================= */

    @Override
    public int getBodyLength() {
        // Status(4) + AuthenticatorServer(16) + ServerVersion(1)
        return 21;
    }

    @Override
    public void encodeBody(ByteBuf buf) {
        buf.writeInt(status);
        buf.writeBytes(authenticatorServer);
        buf.writeByte(serverVersion);
    }

    /* ================= 解码 ================= */

    @Override
    public void decodeBody(ByteBuf buf) {
        status = buf.readInt();
        authenticatorServer = new byte[16];
        buf.readBytes(authenticatorServer);
        serverVersion = buf.readByte();
    }

    @Override
    public void initHeader() {
        SmgpHeader header = new SmgpHeader(SmgpConstants.SMGP_LOGIN_RESP, SequenceIdGenerator.next());
        this.setHeader(header);
    }

    /* ================= 服务端构造 ================= */

    /**
     * AuthenticatorServer = MD5(Status + SharedSecret)
     */
    public static SmgpLoginResp build(int status, String sharedSecret) {
        SmgpLoginResp resp = new SmgpLoginResp();
        resp.status = status;
        resp.authenticatorServer = genAuthenticatorServer(status, sharedSecret);
        return resp;
    }

    public static byte[] genAuthenticatorServer(int status, String secret) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(ByteBuffer.allocate(4).putInt(status).array());
            md5.update(secret.getBytes(StandardCharsets.US_ASCII));
            return md5.digest();
        } catch (Exception e) {
            throw new RuntimeException("genAuthenticatorServer error", e);
        }
    }

    /* Getter / Setter */
}
