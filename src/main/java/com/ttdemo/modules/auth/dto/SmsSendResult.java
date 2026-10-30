package com.ttdemo.modules.auth.dto;

/**
 * @param resendInterval 下次可重发间隔（秒）
 * @param expireIn       验证码有效期（秒）
 * @param debugCode      仅 mock 通道返回，方便本地联调；aliyun 通道为 null
 */
public record SmsSendResult(long resendInterval, long expireIn, String debugCode) {
}
