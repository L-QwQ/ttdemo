package com.ttdemo.modules.auth.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.config.SmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 阿里云短信通道。启用方式：app.sms.provider=aliyun 并配置 AccessKey / 签名 / 模板。
 * 模板内容需包含 ${code} 变量。
 */
@Component
@ConditionalOnProperty(prefix = "app.sms", name = "provider", havingValue = "aliyun")
public class AliyunSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsSender.class);

    private final SmsProperties smsProperties;
    private final Client client;

    public AliyunSmsSender(SmsProperties smsProperties) {
        this.smsProperties = smsProperties;
        SmsProperties.Aliyun aliyun = smsProperties.getAliyun();
        if (isBlank(aliyun.getAccessKeyId()) || isBlank(aliyun.getAccessKeySecret())) {
            throw new IllegalStateException("app.sms.provider=aliyun 时必须配置 app.sms.aliyun.access-key-id / access-key-secret");
        }
        Config config = new Config()
                .setAccessKeyId(aliyun.getAccessKeyId())
                .setAccessKeySecret(aliyun.getAccessKeySecret())
                .setEndpoint(aliyun.getEndpoint());
        try {
            this.client = new Client(config);
        } catch (Exception e) {
            throw new IllegalStateException("初始化阿里云短信客户端失败", e);
        }
    }

    @Override
    public void send(String phone, String code, SmsScene scene) {
        SmsProperties.Aliyun aliyun = smsProperties.getAliyun();
        if (isBlank(aliyun.getSignName()) || isBlank(aliyun.getTemplateCode())) {
            throw new BizException(ResultCode.SMS_SEND_FAILED, "短信签名或模板未配置");
        }
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(aliyun.getSignName())
                .setTemplateCode(aliyun.getTemplateCode())
                .setTemplateParam("{\"code\":\"" + code + "\"}");
        try {
            SendSmsResponse response = client.sendSms(request);
            String responseCode = response.getBody() == null ? null : response.getBody().getCode();
            if (!"OK".equals(responseCode)) {
                String message = response.getBody() == null ? "未知错误" : response.getBody().getMessage();
                log.error("阿里云短信发送失败 phone={} code={} message={}", phone, responseCode, message);
                throw new BizException(ResultCode.SMS_SEND_FAILED);
            }
            log.info("阿里云短信发送成功 phone={} scene={}", phone, scene);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("阿里云短信发送异常 phone={}", phone, e);
            throw new BizException(ResultCode.SMS_SEND_FAILED);
        }
    }

    @Override
    public String provider() {
        return "aliyun";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
