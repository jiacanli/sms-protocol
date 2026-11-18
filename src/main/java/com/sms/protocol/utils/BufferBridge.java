package com.sms.protocol.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.mina.core.buffer.IoBuffer;

/**
 * Mina IoBuffer 与 Netty ByteBuf 互转桥接工具。
 * <p>
 * 用途：让基于 Netty ByteBuf 的消息实体（encodeBody / decodeBody）在 Mina 框架下直接复用，
 * 生产环境（Mina + 第三方 jar）替换第三方 jar 时，只换编解码适配层、实体层零改动：
 * <ul>
 *   <li>解码方向：Mina 收到报文 → {@link #toNetty(IoBuffer, int)} 转 ByteBuf → 实体 decodeBody</li>
 *   <li>编码方向：实体 encodeBody 写 ByteBuf → {@link #toMina(ByteBuf)} 转 IoBuffer → Mina 写出</li>
 * </ul>
 * 两侧默认均为大端序（BIG_ENDIAN），字节语义一致；桥接为一次 byte[] 拷贝，协议报文量级下开销可忽略。
 */
public final class BufferBridge {

    private BufferBridge() {
    }

    /** 解码：将 Mina IoBuffer 的全部可读字节转为 Netty ByteBuf（零拷贝包装），并消费 IoBuffer。 */
    public static ByteBuf toNetty(IoBuffer in) {
        return toNetty(in, in.remaining());
    }

    /**
     * 解码：读取 Mina IoBuffer 的 length 字节转为 Netty ByteBuf（零拷贝包装），并消费 length 字节。
     * 用于拆包后按定长读取消息体。
     */
    public static ByteBuf toNetty(IoBuffer in, int length) {
        byte[] bytes = new byte[length];
        in.get(bytes);
        return Unpooled.wrappedBuffer(bytes);
    }

    /** 编码：将 Netty ByteBuf 的全部可读字节转为 Mina IoBuffer，并消费 ByteBuf。 */
    public static IoBuffer toMina(ByteBuf out) {
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        return IoBuffer.wrap(bytes);
    }

    /**
     * 编码：将 Netty ByteBuf 的全部可读字节追加写入目标 Mina IoBuffer（便于与头部字段连续组装），
     * 并消费 ByteBuf；调用方负责 putInt/putLong 等头部写入及最终 flip()。
     */
    public static IoBuffer writeTo(IoBuffer target, ByteBuf out) {
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        target.put(bytes);
        return target;
    }
}
