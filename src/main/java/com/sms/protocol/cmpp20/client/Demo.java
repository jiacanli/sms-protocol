package com.sms.protocol.cmpp20.client;

import com.sms.protocol.utils.CMPPLongSmsConstants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Demo {

    public static void main(String[] args) throws Exception {
        CMPPClientConfig config = new CMPPClientConfig();
        config.setHost("127.0.0.1");
        config.setPort(7890);
        config.setSourceAddr("901234");
        config.setSharedSecret("1234");
        config.setSplitSendIntervalMs(20);

        CMPPClient client = new CMPPClient(config);
        client.start();

        // 等登录成功（生产建议用 CountDownLatch / 回调）
        Thread.sleep(2000);

        String content = "这是一条测试长短信，内容超过了70个汉字，需要被拆分为多条短信分别提交给网关，"
                + "手机终端会根据UDH协议头自动将分片重组为一条完整的短信展示给用户。";

        String content1="这是一条测试长短信";

        // 单号码发送长短信

//        );

        for (;;){
            if (client.isLoggedIn()){
                client.sendSms(
                "10690001",
                "13800138000",
                "SVC001",
                        content,
                CMPPLongSmsConstants.MSG_FMT_UCS2);
                log.info("send sms success ");
                break;
            }
            Thread.sleep(1000);
        }

    }
}
