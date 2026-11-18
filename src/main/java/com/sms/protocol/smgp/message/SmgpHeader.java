package com.sms.protocol.smgp.message;


import lombok.Data;

@Data
public class SmgpHeader {
    /**
     * 数据包长度 (4字节)
     * Header(12) + Body 的总长度
     */
    private int packetLength;

    /**
     * 请求标识 (4字节)
     * 例如: 0x00000002 (Submit)
     */
    private int commandId;

    /**
     * 消息流水号 (4字节)
     * 需保证唯一，用于匹配请求和响应
     */
    private int sequenceId;

    public SmgpHeader() {}

    public SmgpHeader(int commandId, int sequenceId) {
        this.commandId = commandId;
        this.sequenceId = sequenceId;
    }

    public void setCommandId(int commandId) {
        this.commandId = commandId;
    }

    public int getCommandId() {
        return commandId;
    }

    public void setSequenceId(int sequenceId) {
        this.sequenceId = sequenceId;
    }

    public int getSequenceId() {
        return sequenceId;
    }

    public void setPacketLength(int packetLength) {
        this.packetLength = packetLength;
    }

    public int getPacketLength() {
        return packetLength;
    }
}
