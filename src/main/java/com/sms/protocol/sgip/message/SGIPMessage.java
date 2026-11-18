package com.sms.protocol.sgip.message;


import io.netty.buffer.ByteBuf;

/**
 * SGIP 1.2 协议消息公共接口
 * 所有请求/响应消息实体均需实现此接口
 */
public interface SGIPMessage {

    /**
     * 将消息体字段序列化写入 ByteBuf
     * 仅写入消息体部分，消息头由全局编解码器统一处理
     * @param out 输出字节缓冲区
     */
    void writeBody(ByteBuf out);

    /**
     * 获取消息头
     * @return SGIP 消息头对象
     */
    SGIPHeader getHeader();

    /**
     * 设置消息头
     * @param header SGIP 消息头对象
     */
    void setHeader(SGIPHeader header);

    /**
     * 默认方法：获取命令ID
     * 直接从消息头读取，与 CMPP 调用方式保持一致，方便 Handler 做分支判断
     * @return 命令ID
     */
    default int getCommandId() {
        return getHeader().getCommandId();
    }

    /**
     * 默认方法：生成消息唯一序列号标识
     * SGIP 序列号为三段式：源节点号 + 目的节点号 + 流水号
     * 用于请求-响应匹配、消息去重等场景
     * @return 拼接后的唯一序列号
     */
    default String getSequenceKey() {
        SGIPHeader header = getHeader();
        return header.getSourceNodeId() + "_" + header.getDestNodeId() + "_" + header.getSequenceNumber();
    }
}

