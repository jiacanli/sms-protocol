# sms-protocol

基于 Netty 的 **SMGP（中国电信）/ CMPP 2.0（中国移动）/ SGIP（中国联通）** 三协议短信网关协议栈。

目标是替代生产环境 `Mina + Apollo + 第三方 jar 编解码器` 中的第三方编解码组件：协议实体、编解码、客户端（以及 SGIP 服务端）全部自研，同时提供 **Netty 原生** 与 **Mina 适配** 两套运行方式，并支持由配置中心驱动的运行时编解码切换。

---

## 目录

- [特性](#特性)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [目录结构](#目录结构)
- [能力矩阵](#能力矩阵)
- [核心设计](#核心设计)
- [使用示例](#使用示例)
- [配置项](#配置项)
- [接入 Mina 生产环境](#接入-mina-生产环境)
- [协议要点](#协议要点)
- [验证情况](#验证情况)
- [已知限制](#已知限制)

---

## 特性

- **三协议实体完整**：SMGP 3.0、CMPP 2.0、SGIP 的请求/响应实体，字段顺序与长度按规范定义，`encodeBody` / `decodeBody` 直接操作 Netty `ByteBuf`。
- **双框架复用**：同一套实体与编解码逻辑既可在 Netty 下运行，也可通过 `BufferBridge`（`IoBuffer ↔ ByteBuf`）在 Mina 下直接复用，实体层零改动。
- **两种运行时切换粒度**
  - 报文级：`*CodecKit` 静态方法，在生产既有 encoder/decoder 内 `if/else` 调用，**配置变更后下一个报文即生效，不需要关闭连接**。
  - 会话级：`DynamicProtocolCodecFactory`，按配置选择 `self` / `legacy` 实现，对**新建连接**生效。
- **长短信拆分**：SMGP / CMPP / SGIP 各有一套拆分工具，支持 6 字节与 8 字节 UDH、TLV 关联字段，按编码计算单条容量。
- **客户端能力完整**：连接管理、断线自动重连、登录等待、读空闲心跳、`sequenceId → CompletableFuture` 响应关联、Exit 注销。
- **状态报告兼容**：SMGP 状态报告由 `Deliver.IsReport = 1` 携带，按 `msgFormat` 严格解码（UCS2 / GBK / ASCII / Binary）。
- **Java 8**：`maven.compiler.release=8` 同时约束源码、字节码与 API，编译期即可拦截 Java 9+ 用法。

---

## 环境要求

| 项 | 版本 |
|---|---|
| JDK | 8 及以上（生产为 8，构建用 `release=8`） |
| Maven | 3.6+ |
| 运行依赖 | `netty-all 4.1.43.Final`、`mina-core 2.0.23`（仅桥接用）、`slf4j-api 2.0.17`、`logback-classic 1.4.14`、`lombok 1.18.24` |

```bash
mvn clean package
```

产物：`target/sms-protocol-1.0-SNAPSHOT.jar`

---

## 目录结构

```
src/main/java/com/sms/protocol/
├── smgp/                    SMGP 3.0（中国电信）
│   ├── SmgpConstants.java        命令字、编码格式、TLV Tag
│   ├── SmgpMessageFactory.java   commandId → 实体
│   ├── message/                  登录/提交/下发/心跳/转发/注销/查询 实体
│   ├── codec/                    Netty Encoder/Decoder
│   ├── codec/mina/               SmgpCodecKit + Mina 适配 + 动态工厂
│   └── client/                   SmgpClient / Config / Handler / Listener
├── cmpp20/                  CMPP 2.0（中国移动）
│   ├── message/                  Connect/Submit/Deliver/ActiveTest 实体（含 Header）
│   ├── codec/                    Netty Encoder/Decoder
│   ├── codec/mina/               CMPPCodecKit + Mina 适配
│   └── client/                   CMPPClient / Config / Handler
├── sgip/                    SGIP（中国联通）
│   ├── constant/SGIPCommand.java 命令字
│   ├── message/                  Bind/Submit/Deliver/Report/Unbind 实体
│   ├── codec/                    Netty Encoder/Decoder
│   ├── codec/mina/               SGIPCodecKit + Mina 适配
│   ├── client/                   SGIPClient
│   ├── server/                   SGIPServer（接收 SMG 推送）
│   └── utils/                    SGIP 长短信工具
└── utils/                   公共能力
    ├── BufferBridge.java              IoBuffer ↔ ByteBuf
    ├── CodecImplSelector.java         编解码实现标识提供者（配置中心驱动）
    ├── DynamicProtocolCodecFactory.java  协议无关的动态工厂
    ├── SmgpLongSmsUtil.java           SMGP 长短信拆分
    ├── CMPPLongSmsUtil.java           CMPP 长短信拆分
    ├── Cmpp2AuthCalculator.java       CMPP 2.0 登录鉴权（MD5）
    └── SequenceIdGenerator.java       报文序列号
```

---

## 能力矩阵

| 协议 | 客户端 | 服务端 | 心跳 | 长短信 | 状态报告 | 备注 |
|---|---|---|---|---|---|---|
| SMGP 3.0 | ✅ `SmgpClient` | — | ActiveTest | ✅ `SmgpLongSmsUtil` | ✅ `SmgpReport`（Deliver 内嵌） | 长连接接收下发行 |
| CMPP 2.0 | ✅ `CMPPClient` | — | ActiveTest | ✅ `CMPPLongSmsUtil` | ✅ `registeredDelivery=1` 时解析 | 登录鉴权走 `Cmpp2AuthCalculator` |
| SGIP 1.2 | ✅ `SGIPClient` | ✅ `SGIPServer` | ActiveTest | ✅ `SGIPLongSmsUtil` | ✅ 独立 `SGIPReport` 命令 | Server 用于接收 SMG 推送的 Deliver / Report |

> SMGP 与 CMPP 在运营商组网中由 SP 主动连接网关，网关复用同一连接下发 Deliver / 状态报告，因此只需客户端；SGIP 部分省份由 SMG 主动连接 SP，故额外提供服务端。

---

## 核心设计

### 1. 实体层：统一基于 ByteBuf

每个报文实体继承协议基类（`SmgpMessage` / `CMPPMessage` / `SGIPMessage`），实现：

```java
public abstract int getBodyLength();
public abstract void encodeBody(ByteBuf buf);
public abstract void decodeBody(ByteBuf buf);
public abstract void initHeader();
```

Header 独立建模（`SmgpHeader` 12 字节、`CMPPHeader` 12 字节、`SGIPHeader` 20 字节），序列号由 `SequenceIdGenerator` 统一生成。

### 2. BufferBridge：Mina 与 Netty 互转

```java
ByteBuf buf = BufferBridge.toNetty(ioBuffer, length);  // 解码方向
IoBuffer out = BufferBridge.toMina(byteBuf);           // 编码方向
IoBuffer out = BufferBridge.writeTo(target, byteBuf);  // 追加写入（头部已写时）
```

两侧均为大端序，字节语义一致；一次 `byte[]` 拷贝，短信报文量级下开销可忽略。

### 3. CodecKit：报文级切换（推荐生产使用）

把编解码核心逻辑抽成静态方法，在生产现有的三方 encoder/decoder 内按配置 `if/else`，**不重连、下一报文生效**：

```java
public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
    if (codecSwitch.current().isNew()) {
        SmgpCodecKit.decodeFrames(session, in, out::write);   // 自研实现
        return;
    }
    // ...原第三方 jar 逻辑...
}
```

编码侧对应 `SmgpCodecKit.encodeFrame(msg)`，CMPP / SGIP 同理。

### 4. DynamicProtocolCodecFactory：会话级切换

Mina 的 `ProtocolCodecFilter` 在会话创建时绑定一次 encoder/decoder，因此工厂分发**只对新建连接生效**：

```text
配置中心变更 → CodecImplSelector 状态更新 → 关闭既有会话触发重连 → 新会话使用新实现
```

未知标识回退到默认实现（`self`），切换失败不致瘫痪。

### 5. CodecImplSelector：配置中心驱动

```java
@FunctionalInterface
public interface CodecImplSelector {
    String current();   // 返回 "self" / "legacy"
}
```

生产实现建议：Apollo `ConfigChangeListener` 中更新 `volatile` 字段，`current()` 只读内存，**运行时无 IO**，不影响会话创建与解析路径。

### 6. MsgID 双格式兼容

SMGP `SubmitResp.MsgID` 为 10 字节 Octet String，实际存在两种编码：

| 格式 | 说明 |
|---|---|
| ASCII 定长 | 标准实现，`0x00` 结尾截断 |
| `2 字节 0x00 + 8 字节 long` | 部分网关/挡板实现，网络序大端 |

实体同时暴露 `msgId`（统一文本）与 `msgIdLong`（原始数值），编码时按原格式对称写回，保证查询/对账不丢精度。

### 7. 状态报告

- SMGP：`Deliver.IsReport = 1`，`MsgContent` 为 SMPP 风格文本（`id:… stat:DELIVRD err:…`），`SmgpReport.parse` 解析。
- CMPP / SGIP：独立报告体 / 独立命令，按各自 `msgFormat` 解码。

**严格按 `msgFormat` 解码**：不循环试探编码，避免 GBK 内容被误判为 UTF-16 导致乱码或静默错解。

---

## 使用示例

### SMGP 发送短信

```java
SmgpClientConfig cfg = new SmgpClientConfig();
cfg.setHost("gw.example.com");
cfg.setPort(8890);
cfg.setClientId("17101197");       // SP 企业代码，8 字节
cfg.setSecret("112233");           // 共享密钥
cfg.setSrcId("10691234");          // 接入号

SmgpClient client = new SmgpClient(cfg, new SmgpMessageListener() {
    @Override public void onSubmitResp(SmgpSubmitResp resp) {
        // resp.getMsgId() / resp.getMsgIdLong() / resp.getStatus()
    }
    @Override public void onDeliver(SmgpDeliver deliver) {
        // 上行 MO
    }
    @Override public void onReport(SmgpReport report, SmgpDeliver deliver) {
        // 状态报告：report.getStat() / getMsgId()
    }
});

client.start();
if (client.awaitLogin(10, TimeUnit.SECONDS)) {
    List<CompletableFuture<SmgpSubmitResp>> futures =
            client.sendSms("13800138000", "你好，这是一条长短信测试……",
                           SmgpConstants.MSG_FORMAT_UCS2);
    for (CompletableFuture<SmgpSubmitResp> f : futures) {
        SmgpSubmitResp resp = f.get(30, TimeUnit.SECONDS);
    }
}
client.stop();
```

> `sendSms` 自动按编码容量拆分长短信，返回每个分片的 `SubmitResp` future；`sendSmsMultiDest` 用于多号码群发（每个号码独立分片组）。

### CMPP 发送短信

```java
CMPPClientConfig cfg = new CMPPClientConfig();
cfg.setHost("gw.example.com");
cfg.setPort(7890);
cfg.setSourceAddr("901234");       // 6 字节企业代码
cfg.setSharedSecret("123456");

CMPPClient client = new CMPPClient(cfg);
client.start();
client.sendSms("1069000000", "13800138000", "test", "你好", 8);  // msgFmt=8 UCS2
client.stop();
```

### SGIP 服务端接收推送

```java
SGIPServerConfig cfg = new SGIPServerConfig(8890, 3030123456L, "spname", "sppwd");
SGIPServer server = new SGIPServer(cfg, new SGIPServerListener() {
    @Override public void onDeliver(SGIPDeliver deliver) { }
    @Override public void onReport(SGIPReport report) { }
});
server.start();          // 阻塞等待 Bind
server.waitUntilShutdown();
```

### 长短信单独拆分（不进客户端）

```java
List<SmgpSubmit> pieces = SmgpLongSmsUtil.split(content, SmgpConstants.MSG_FORMAT_UCS2);
// 只填充分片相关字段，业务字段（srcTermId / destTermIds / serviceId 等）由调用方设置
```

---

## 配置项

### SmgpClientConfig

| 字段 | 默认 | 说明 |
|---|---|---|
| `host` / `port` | 127.0.0.1 / 8890 | 网关地址与端口 |
| `clientId` | — | SP 企业代码（8 字节） |
| `secret` | — | 共享密钥，用于计算 `AuthenticatorClient` |
| `loginMode` | 2（长连接） | 1=短连接，2=长连接 |
| `serviceId` | test | 业务代码（10 字节） |
| `srcId` | — | 接入号（21 字节） |
| `connectTimeout` | 5000 ms | 连接超时 |
| `readerIdleSeconds` | 30 s | 读空闲超过则发 ActiveTest |
| `reconnectEnabled` | true | 断线自动重连 |
| `reconnectIntervalSeconds` | 5 s | 重连间隔 |
| `loginTimeoutSeconds` | 10 s | 登录等待超时 |
| `submitTimeoutMillis` | 30000 ms | 等待 SubmitResp 超时 |

### CMPPClientConfig

| 字段 | 默认 | 说明 |
|---|---|---|
| `host` / `port` | — | 网关地址与端口 |
| `sourceAddr` | — | 6 字节企业代码 |
| `sharedSecret` | — | 6 字节共享密钥 |
| `connectTimeout` | 5000 ms | 连接超时 |
| `readerIdleSeconds` | 30 s | 心跳间隔 |
| `splitSendIntervalMs` | 20 ms | 长短信分片发送间隔，避免瞬间刷屏 |

### SGIPServerConfig

| 字段 | 默认 | 说明 |
|---|---|---|
| `port` | 8890 | 监听端口 |
| `sourceNodeId` | — | SP 节点号（响应源节点） |
| `allowedClientNodeId` | -1 | 允许的对端 SMG 节点号，-1 不校验 |
| `loginName` / `loginPassword` | — | SMG 连接 SP 使用的账号密码 |
| `readerIdleSeconds` | 60 s | 读空闲超时 |
| `writerIdleSeconds` | 0 | 写空闲，0 表示不主动心跳 |
| `businessThreads` | 0 | 业务线程池大小，0 用 IO 线程直接处理 |

---

## 接入 Mina 生产环境

1. **保留现有 Mina 装配**，只替换编解码实现。
2. 生产 decoder/encoder 内按 `CodecImplSelector` 判断，调用对应 `*CodecKit`（报文级切换，无需重连）。
3. Apollo 配置项建议按协议分开，便于单独回退：

```yaml
sms:
  codec:
    smgp:
      impl: self        # self | legacy
      legacyClass: com.vendor.smgp.SmgpCodecFactory
    cmpp:
      impl: self
    sgip:
      impl: self
```

4. 需要"整连接切换"时再用 `DynamicProtocolCodecFactory`：切换后关闭既有会话，重连即生效。
5. 第三方 jar 的 `ProtocolCodecFactory` 通过 `withLegacy(selector, selfFactory, legacyClassName)` 反射装载，要求无参构造。

---

## 协议要点

### SMGP 3.0

| 命令 | CommandId | 说明 |
|---|---|---|
| Login / LoginResp | `0x00000001` / `0x80000001` | 登录，`LoginMode` 区分长短连接 |
| Submit / SubmitResp | `0x00000002` / `0x80000002` | 提交短信 |
| Deliver / DeliverResp | `0x00000003` / `0x80000003` | 下发行（MO / 状态报告） |
| ActiveTest / Resp | `0x00000004` / `0x80000004` | 链路检测，无消息体 |
| Exit / ExitResp | `0x00000006` / `0x80000006` | 注销，无消息体 |
| Query / QueryResp | `0x00000007` / `0x80000007` | 查询发送状态（可选） |

- 消息格式：`0` ASCII、`4` Binary、`8` UCS2（中文常用）、`15` GBK。
- 长短信：`TP_UDHI = 1` + TLV `PK_TOTAL / PK_NUMBER`（Tag `0x0009 / 0x000A`）关联分片。
- Header 12 字节：`PacketLength(4) + CommandId(4) + SequenceId(4)`。

### CMPP 2.0

- Header 12 字节：`TotalLength(4) + CommandId(4) + SequenceId(4)`。
- 登录鉴权：`MD5(SourceAddr + 9 字节 0x00 + SharedSecret + Timestamp)`，见 `Cmpp2AuthCalculator`。
- `SubmitResp.MsgId` 为 **8 字节 long**（与 SMGP 的 10 字节 Octet String 不同）。

### SGIP

- Header 20 字节：`PacketLength(4) + CommandId(4) + 源节点号(4) + 目的节点号(4) + 流水号(4)`，后三段共 12 字节构成序列号。
- `Submit / Deliver / Report / Bind / Unbind` 各自独立命令字，状态报告不依赖 Deliver。
- 长短信 UDH 与 SMGP/CMPP 一致，终端侧自动合并。

---

## 验证情况

| 验证项 | 方式 | 结果 |
|---|---|---|
| 编码 → 解码 round-trip | 三协议全部实体逐一构包再解包，比对字段 | 通过 |
| 端到端联调 | 临时 Netty 模拟网关：登录、心跳、SubmitResp 关联、Deliver/状态报告回调、断线重连 | 通过 |
| Java 8 兼容 | `release=8` 编译（Maven 3.9 + JDK 17） | 通过，无 Java 9+ API |
| 长短信 | 多分片 UDH/TLV 组装与对端解析比对 | 通过 |

---

## 已知限制

- **CMPP 目前只有客户端**。若组网要求 SP 监听端口接收推送，需要参照 `SGIPServer` 扩展。
- **SMGP 只有客户端**：Deliver/状态报告由网关复用登录连接下发。
- `Demo`、`SGIPDemo` 为手动演示入口，不参与自动测试。
- 未发布到 Maven 中央仓库，其他工程引用需先 `mvn install` 到本地/私服。
- 编解码为纯 Java 实现，未做压测；高并发场景建议先用模拟网关做吞吐基线。

---

## License

未指定开源许可证。如需对外分发，请先补充 `LICENSE` 文件。
