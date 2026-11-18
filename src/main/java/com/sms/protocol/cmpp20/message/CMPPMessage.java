package com.sms.protocol.cmpp20.message;

import io.netty.buffer.ByteBuf;

public interface CMPPMessage {
    CMPPHeader getHeader();
    void setHeader(CMPPHeader header);
    void writeBody(ByteBuf out);

    /** 命令字，默认从 header 取 */
    default int getCommandId() {
        return getHeader().getCommandId();
    }

    /** 流水号，默认从 header 取 */
    default int getSequenceId() {
        return getHeader().getSequenceId();
    }

}
