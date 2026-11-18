package com.sms.protocol.smgp.codec.mina;

import com.sms.protocol.utils.CodecImplSelector;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 SMGP 编解码工厂：在运行时根据配置决定使用哪套编解码实现。
 * <p>
 * 用途：生产环境通过配置中心（如 Apollo）动态切换编解码实现，应对紧急情况无需重启：
 * <ul>
 *   <li>{@code self}：本工程自研 Mina 适配层（{@link SmgpMinaCodecFactory}）</li>
 *   <li>{@code legacy}：第三方 jar 的 ProtocolCodecFactory（类名可配置，反射加载）</li>
 * </ul>
 *
 * <p><b>生效边界（Mina 机制约束）</b>：ProtocolCodecFilter 在会话创建时绑定一次 encoder/decoder，
 * 因此本工厂的动态分发只对<b>新建连接</b>生效；既有连接需要关闭重连才会吃到新配置。
 * 生产切换流程：配置中心变更 → selector 状态更新 → 关闭既有会话触发重连 → 新会话自动使用新实现。
 *
 * <p><b>回退保护</b>：配置值为未知标识时回退到默认实现（默认 self），切换失败不致瘫痪；
 * 若默认实现也不可用则抛 {@link IllegalStateException}（启动期即应暴露装配错误）。
 */
public class SmgpDynamicCodecFactory implements ProtocolCodecFactory {

    private static final Logger logger = LoggerFactory.getLogger(SmgpDynamicCodecFactory.class);

    /** 默认实现标识：自研 Mina 适配层 */
    public static final String IMPL_SELF = "self";

    /** 三方 jar 实现的约定标识 */
    public static final String IMPL_LEGACY = "legacy";

    private final CodecImplSelector selector;
    private final Map<String, ProtocolCodecFactory> factories;
    private final String defaultKey;

    /**
     * @param selector  当前实现标识提供者（配置中心驱动），null 时恒用默认实现
     * @param factories 实现标识 → 编解码工厂 的映射，需包含默认实现
     * @param defaultKey 默认实现标识，未知配置时回退目标；null 时使用 {@link #IMPL_SELF}
     */
    public SmgpDynamicCodecFactory(CodecImplSelector selector, Map<String, ProtocolCodecFactory> factories, String defaultKey) {
        this.selector = selector;
        this.factories = new ConcurrentHashMap<>(factories);
        this.defaultKey = defaultKey == null ? IMPL_SELF : defaultKey;
    }

    public SmgpDynamicCodecFactory(CodecImplSelector selector, Map<String, ProtocolCodecFactory> factories) {
        this(selector, factories, IMPL_SELF);
    }

    @Override
    public ProtocolEncoder getEncoder(IoSession session) throws Exception {
        return currentFactory().getEncoder(session);
    }

    @Override
    public ProtocolDecoder getDecoder(IoSession session) throws Exception {
        return currentFactory().getDecoder(session);
    }

    private ProtocolCodecFactory currentFactory() {
        String key = selector == null ? defaultKey : selector.current();
        ProtocolCodecFactory factory = factories.get(key);
        if (factory == null) {
            logger.warn("Unknown SMGP codec impl '{}', fallback to default '{}'", key, defaultKey);
            factory = factories.get(defaultKey);
        }
        if (factory == null) {
            throw new IllegalStateException("No SMGP codec factory available for key: " + key
                    + ", default: " + defaultKey);
        }
        return factory;
    }

    /**
     * 便捷装配：自研实现 + 可选的第三方 jar 实现（类名反射加载，要求无参构造）。
     *
     * @param selector        实现标识提供者
     * @param legacyClassName 第三方 jar 的 ProtocolCodecFactory 全类名；null/空白则仅注册 self
     */
    public static SmgpDynamicCodecFactory withLegacy(CodecImplSelector selector, String legacyClassName) throws Exception {
        Map<String, ProtocolCodecFactory> factories = new ConcurrentHashMap<>();
        factories.put(IMPL_SELF, new SmgpMinaCodecFactory());
        if (legacyClassName != null && !legacyClassName.trim().isEmpty()) {
            factories.put(IMPL_LEGACY, loadFactory(legacyClassName));
        }
        return new SmgpDynamicCodecFactory(selector, factories);
    }

    /** 反射加载第三方 ProtocolCodecFactory 实例（要求无参构造） */
    public static ProtocolCodecFactory loadFactory(String className) throws Exception {
        Class<?> clazz = Class.forName(className);
        return (ProtocolCodecFactory) clazz.getDeclaredConstructor().newInstance();
    }
}
