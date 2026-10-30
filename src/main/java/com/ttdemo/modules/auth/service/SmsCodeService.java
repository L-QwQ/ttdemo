package com.ttdemo.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.common.util.PhoneUtils;
import com.ttdemo.config.SmsProperties;
import com.ttdemo.modules.auth.dto.SmsSendResult;
import com.ttdemo.modules.auth.entity.SmsCode;
import com.ttdemo.modules.auth.mapper.SmsCodeMapper;
import com.ttdemo.modules.auth.sms.SmsScene;
import com.ttdemo.modules.auth.sms.SmsSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 验证码生成、限流、校验。当前落库存储，后续可替换为 Redis 实现（接口不变）。
 */
@Service
public class SmsCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsCodeMapper smsCodeMapper;
    private final SmsSender smsSender;
    private final SmsProperties properties;

    public SmsCodeService(SmsCodeMapper smsCodeMapper, SmsSender smsSender, SmsProperties properties) {
        this.smsCodeMapper = smsCodeMapper;
        this.smsSender = smsSender;
        this.properties = properties;
    }

    public SmsSendResult send(String phone, SmsScene scene, String ip) {
        if (!PhoneUtils.isValid(phone)) {
            throw new BizException(ResultCode.PHONE_FORMAT_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        assertNotTooFrequent(phone, scene, now);
        assertDailyLimit(phone, now);

        String code = generateCode();
        SmsCode entity = new SmsCode();
        entity.setPhone(phone);
        entity.setCode(code);
        entity.setScene(scene.name());
        entity.setExpiredAt(now.plus(properties.getExpire()));
        entity.setUsed(0);
        entity.setAttempts(0);
        entity.setRequestIp(ip);
        smsCodeMapper.insert(entity);

        smsSender.send(phone, code, scene);

        String debugCode = "mock".equals(smsSender.provider()) ? code : null;
        return new SmsSendResult(properties.getResendInterval().toSeconds(), properties.getExpire().toSeconds(), debugCode);
    }

    /**
     * 校验验证码，成功后立即标记已使用（一次性）
     */
    public void verify(String phone, SmsScene scene, String code) {
        SmsCode record = smsCodeMapper.selectOne(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getScene, scene.name())
                .eq(SmsCode::getUsed, 0)
                .orderByDesc(SmsCode::getId)
                .last("limit 1"));
        if (record == null) {
            throw new BizException(ResultCode.SMS_CODE_ERROR, "请先获取验证码");
        }
        if (record.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.SMS_CODE_EXPIRED);
        }
        if (record.getAttempts() != null && record.getAttempts() >= properties.getMaxAttempts()) {
            throw new BizException(ResultCode.SMS_CODE_ERROR, "验证码错误次数过多，请重新获取");
        }
        if (!record.getCode().equals(code)) {
            smsCodeMapper.update(null, new LambdaUpdateWrapper<SmsCode>()
                    .eq(SmsCode::getId, record.getId())
                    .setSql("attempts = attempts + 1"));
            throw new BizException(ResultCode.SMS_CODE_ERROR);
        }
        int updated = smsCodeMapper.update(null, new LambdaUpdateWrapper<SmsCode>()
                .eq(SmsCode::getId, record.getId())
                .eq(SmsCode::getUsed, 0)
                .set(SmsCode::getUsed, 1));
        if (updated == 0) {
            throw new BizException(ResultCode.SMS_CODE_USED);
        }
    }

    private void assertNotTooFrequent(String phone, SmsScene scene, LocalDateTime now) {
        SmsCode latest = smsCodeMapper.selectOne(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getScene, scene.name())
                .orderByDesc(SmsCode::getId)
                .last("limit 1"));
        if (latest == null || latest.getCreatedAt() == null) {
            return;
        }
        Duration elapsed = Duration.between(latest.getCreatedAt(), now);
        long interval = properties.getResendInterval().toSeconds();
        if (elapsed.toSeconds() < interval) {
            long wait = interval - elapsed.toSeconds();
            throw new BizException(ResultCode.SMS_SEND_TOO_FREQUENT, "请 " + wait + " 秒后再获取验证码");
        }
    }

    private void assertDailyLimit(String phone, LocalDateTime now) {
        Long count = smsCodeMapper.selectCount(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .ge(SmsCode::getCreatedAt, LocalDate.from(now).atStartOfDay()));
        if (count != null && count >= properties.getDailyLimit()) {
            throw new BizException(ResultCode.SMS_SEND_LIMIT);
        }
    }

    private String generateCode() {
        int length = Math.max(4, Math.min(properties.getCodeLength(), 8));
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
