package com.sms.protocol.utils;

/**
 * 编解码实现选择器：返回当前应使用的编解码实现标识（如 "self" / "legacy"）。
 * <p>
 * 生产环境基于配置中心（Apollo/Nacos/自研）实现：从配置读取实现标识，注册 ConfigChangeListener
 * 在配置变更时更新本地 volatile 状态（读配置只在变更时发生，运行时 getEncoder/getDecoder
 * 无 IO，保证会话创建/报文解析路径的低延迟）。
 */
@FunctionalInterface
public interface CodecImplSelector {

    /** 返回当前应使用的编解码实现标识，未知标识由工厂回退到默认实现 */
    String current();
}
