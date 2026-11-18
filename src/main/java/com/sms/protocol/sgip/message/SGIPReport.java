package com.sms.protocol.sgip.message;


import com.sms.protocol.utils.SGIPCodecUtil;
import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class SGIPReport implements SGIPMessage {
    private SGIPHeader header;

    // ==================== 原Submit消息的12字节序列号（三段式） ====================
    /** 原Submit序列号：源节点号 */
    private long submitSrcNodeId;
    /** 原Submit序列号：目的节点号 */
    private long submitDestNodeId;
    /** 原Submit序列号：流水号 */
    private long submitSequenceNumber;

    /** 报告类型，1字节：0=状态报告 */
    private int reportType;
    /** 接收用户号码，21字节定长 */
    private String userNumber;
    /** 发送状态，1字节：0=成功，1=等待发送，2=发送失败 */
    private int state;
    /** 错误码，1字节，state=2时有效 */
    private int errorCode;
    /** 保留字段，固定8字节 */
    private byte[] reserve = new byte[8];

    @Override
    public void writeBody(ByteBuf out) {
        // 写入12字节原Submit序列号
        SGIPCodecUtil.writeSequenceId(out, submitSrcNodeId, submitDestNodeId, submitSequenceNumber);
        out.writeByte(reportType & 0xFF);
        SGIPCodecUtil.writeCString(out, userNumber, 21);
        out.writeByte(state & 0xFF);
        out.writeByte(errorCode & 0xFF);
        out.writeBytes(reserve);
    }
}

