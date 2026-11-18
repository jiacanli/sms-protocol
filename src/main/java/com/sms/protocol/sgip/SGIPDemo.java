package com.sms.protocol.sgip;

import com.sms.protocol.sgip.client.SGIPClient;
import com.sms.protocol.sgip.message.SGIPDeliver;
import com.sms.protocol.sgip.message.SGIPReport;
import com.sms.protocol.sgip.server.SGIPServer;
import com.sms.protocol.sgip.server.SGIPServerConfig;
import com.sms.protocol.sgip.server.SGIPServerListener;
import com.sms.protocol.sgip.utils.SGIPLongSmsConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * SGIP 协议本地回环演示
 * <p>
 * 演示内容：
 * <ol>
 *   <li>启动 SGIPServer 监听本地端口，模拟 SMG 端接收推送</li>
 *   <li>启动 SGIPClient 连接本地服务端，完成 Bind 登录</li>
 *   <li>客户端发送长短信（自动拆分）</li>
 *   <li>服务端回调打印收到的 Deliver / Report</li>
 * </ol>
 * 实际生产中，Client 连接运营商 SMG 发送 Submit，Server 监听端口接收 SMG 推送的上行短信和状态报告。
 * </p>
 */
@Slf4j
public class SGIPDemo {

    // ==================== 演示配置 ====================
    private static final int SERVER_PORT = 8881;
    private static final long SMG_NODE_ID = 3030000001L;   // 模拟 SMG（网关）节点号
    private static final long SP_NODE_ID  = 3030000002L;   // SP（业务方）节点号
    private static final String LOGIN_NAME = "ZYYH52279";
    private static final String LOGIN_PWD  = "ZYYH52279";
    private static final String SP_ID      = "30471";
    private static final String SP_NUMBER  = "10690001";
    private static final String TEST_PHONE = "13800138000";

    public static void main(String[] args) throws Exception {

        // ==================================================================
        //  第一步：启动 SGIPServer（监听本地端口，接收 Deliver/Report）
        // ==================================================================
        SGIPServerConfig serverConfig = new SGIPServerConfig(
                8882,
                SMG_NODE_ID,       // 服务端节点号（响应时作为源节点号）
                LOGIN_NAME,
                LOGIN_PWD
        );
        serverConfig.setReaderIdleSeconds(60);

        SGIPServer server = new SGIPServer(serverConfig, new SGIPServerListener() {
            @Override
            public void onDeliver(SGIPDeliver deliver) {
                log.info("【服务端】收到上行短信: 来自={}, 内容长度={}, seq={}",
                        deliver.getSrcTerminalId(),
                        deliver.getMsgLength(),
                        deliver.getSequenceKey());
            }

            @Override
            public void onReport(SGIPReport report) {
                log.info("【服务端】收到状态报告: 号码={}, 状态={}, 错误码={}",
                        report.getUserNumber(),
                        report.getState(),
                        report.getErrorCode());
            }

            @Override
            public void onClientConnected(long clientNodeId, String remoteAddr) {
                log.info("【服务端】客户端已连接: nodeId={}, addr={}", clientNodeId, remoteAddr);
            }

            @Override
            public void onClientDisconnected(long clientNodeId, String remoteAddr) {
                log.info("【服务端】客户端已断开: nodeId={}, addr={}", clientNodeId, remoteAddr);
            }
        });

        server.start();
        log.info("========== SGIPServer 已启动，监听端口 {} ==========", 8882);

        // 等待服务端就绪
        Thread.sleep(500);

        // ==================================================================
        //  第二步：启动 SGIPClient（连接本地服务端，发送 Submit）
        // ==================================================================
        SGIPClient client = new SGIPClient(
                "127.0.0.1",     // 服务端地址
                SERVER_PORT,     // 服务端端口
                SP_NODE_ID,      // 源节点号（SP）
                SMG_NODE_ID,     // 目的节点号（SMG）
                LOGIN_NAME,      // 登录账号（需与服务端配置一致）
                LOGIN_PWD,       // 登录密码
                SP_ID,           // SP 企业代码
                SP_NUMBER        // SP 接入号码
        );

        client.connect();
        log.info("========== SGIPClient 已连接 ==========");

        // 等待登录完成
        Thread.sleep(1000);

        if (!client.isLoginSuccess()) {
            log.error("客户端登录失败，退出演示");
            server.stop();
            return;
        }
        log.info("========== 客户端登录成功 ==========");

        // ==================================================================
        //  第三步：发送普通短信
        // ==================================================================
//        log.info("---------- 发送普通短信 ----------");
//        long seq1 = client.submit(TEST_PHONE, "这是一条普通测试短信", SGIPLongSmsConstants.MSG_FMT_UCS2);
//        log.info("普通短信已提交, seq={}", seq1);
//
//        Thread.sleep(500);

        // ==================================================================
        //  第四步：发送长短信（自动拆分）
        // ==================================================================
        for (;;){
            if (!client.isLoginSuccess()){
                continue;
            }
            break;
        }
        log.info("---------- 发送长短信（自动拆分） ----------");
        String longContent = "这是一条SGIP长短信测试内容，用于验证长短信自动拆分功能是否正常工作。"
                + "当短信内容超过单条最大长度时，系统会自动在每条分片前添加UDH协议头，"
                + "手机终端收到后会根据UDH中的参考号和分片序号自动重组为一条完整的短信展示给用户。"
                + "本测试内容包含足够多的文字以触发拆分逻辑，请观察日志中的分片数量。";

        long seq2 = client.submitLongSms(TEST_PHONE, longContent, SGIPLongSmsConstants.MSG_FMT_UCS2);
        log.info("长短信已提交, 首片seq={}", seq2);

        // ==================================================================
        //  保持运行，等待接收服务端推送（实际场景中 SMG 会推送 Deliver/Report）
        // ==================================================================
        log.info("========== 演示发送完成，保持运行中... 按 Ctrl+C 退出 ==========");

        // 注册关闭钩子，优雅退出
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭客户端...");
            client.disconnect();
            log.info("正在关闭服务端...");
            server.stop();
            log.info("已退出");
        }));

        // 阻塞主线程
        Thread.currentThread().join();
    }
}
