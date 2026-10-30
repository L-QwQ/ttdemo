package com.ttdemo.modules.auth.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发/测试通道：不真正发短信，把验证码打到日志里
 */
@Component
@ConditionalOnProperty(prefix = "app.sms", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public void send(String phone, String code, SmsScene scene) {
        log.warn("【模拟短信】手机号={} 场景={} 验证码={}（app.sms.provider=mock，未真实发送）", phone, scene, code);
    }

    @Override
    public String provider() {
        return "mock";
    }
}
