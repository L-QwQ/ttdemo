package com.ttdemo.modules.auth.sms;

/**
 * 短信发送通道。实现类由 app.sms.provider 决定（mock / aliyun）。
 */
public interface SmsSender {

    void send(String phone, String code, SmsScene scene);

    /** 通道名，用于日志与排查 */
    String provider();
}
