package com.sms.protocol.sgip.message;

import lombok.Data;


/**
 * SGIP 1.2 公共消息头（固定20字节）
 * 所有字段类型与编解码层严格一一对应
 */
@Data
public class SGIPHeader {
    /**
     * 消息总长度（含消息头+消息体）
     * 协议：4字节无符号整数，实际范围远小于int上限，用int存储即可
     */
    private int totalLength;

    /**
     * 命令ID
     * 协议：4字节无符号整数，枚举值固定，用int存储
     * 请求：0x000000xx；响应：0x800000xx
     */
    private int commandId;

    /**
     * 源节点号（序列号第1段）
     * 协议：4字节无符号整数，用long存储避免溢出
     */
    private long sourceNodeId;

    /**
     * 目的节点号（序列号第2段）
     * 协议：4字节无符号整数，用long存储避免溢出
     */
    private long destNodeId;

    /**
     * 流水号（序列号第3段）
     * 协议：4字节无符号整数，用long存储避免溢出
     */
    private long sequenceNumber;
}


