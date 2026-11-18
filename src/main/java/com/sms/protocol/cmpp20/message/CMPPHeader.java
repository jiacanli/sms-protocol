package com.sms.protocol.cmpp20.message;

import lombok.Data;

@Data
public class CMPPHeader {
    private int totalLength; // 消息总长度
    private int commandId;   // 命令ID
    private int sequenceId;  // 消息流水号

    public CMPPHeader(int commandId, int sequenceId) {
        this.commandId = commandId;
        this.sequenceId = sequenceId;
    }

    public CMPPHeader(){

    }

}
