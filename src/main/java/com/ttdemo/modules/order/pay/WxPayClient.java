package com.ttdemo.modules.order.pay;

import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.config.WxPayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信支付 v3（Native 扫码 + 回调验签解密）。
 * <p>
 * 只做下单与回调校验，不涉及资金操作；未配置商户参数时调用会抛出明确异常，
 * 因此 mock 通道不受影响。
 */
@Component
public class WxPayClient {

    private static final Logger log = LoggerFactory.getLogger(WxPayClient.class);
    private static final String SCHEMA = "WECHATPAY2-SHA256-RSA2048";

    private final WxPayProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile PrivateKey privateKey;
    private volatile PublicKey platformPublicKey;

    public WxPayClient(WxPayProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    /**
     * Native 下单，返回二维码链接 code_url。
     */
    public String nativePay(String orderNo, int amountFen, String description) {
        requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appid", props.getAppid());
        body.put("mchid", props.getMchId());
        body.put("description", description);
        body.put("out_trade_no", orderNo);
        body.put("notify_url", props.getNotifyUrl());
        body.put("amount", Map.of("total", amountFen, "currency", "CNY"));
        String json = objectMapper.writeValueAsString(body);

        String url = props.getBaseUrl() + "/v3/pay/transactions/native";
        String authorization = buildAuthorization("POST", "/v3/pay/transactions/native", json);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("微信下单失败 status={} body={}", response.statusCode(), response.body());
                throw new BizException(ResultCode.ERROR, "微信下单失败，请稍后重试");
            }
            JsonNode node = objectMapper.readTree(response.body());
            String codeUrl = node.path("code_url").asString("");
            if (codeUrl.isEmpty()) {
                throw new BizException(ResultCode.ERROR, "微信下单返回缺少 code_url");
            }
            return codeUrl;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信下单异常 orderNo={}", orderNo, e);
            throw new BizException(ResultCode.ERROR, "微信下单异常，请稍后重试");
        }
    }

    /**
     * 解析并校验支付回调。
     *
     * @return 解密后的交易对象（包含 out_trade_no / transaction_id / trade_state）
     */
    public Map<String, String> parseNotify(String body, String signature, String timestamp, String nonce) {
        requireConfigured();
        if (signature == null || timestamp == null || nonce == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "回调缺少签名头");
        }
        // 验签：时间戳\n随机串\n报文\n
        String message = timestamp + "\n" + nonce + "\n" + body + "\n";
        if (!verify(message, signature)) {
            throw new BizException(ResultCode.BAD_REQUEST, "回调验签失败");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode resource = root.path("resource");
            String ciphertext = resource.path("ciphertext").asString("");
            String resourceNonce = resource.path("nonce").asString("");
            String associatedData = resource.path("associated_data").asString("");
            if (ciphertext.isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "回调缺少密文");
            }
            String plain = decrypt(ciphertext, resourceNonce, associatedData);
            JsonNode data = objectMapper.readTree(plain);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("orderNo", data.path("out_trade_no").asString(""));
            result.put("transactionId", data.path("transaction_id").asString(""));
            result.put("tradeState", data.path("trade_state").asString(""));
            result.put("successTime", data.path("success_time").asString(""));
            result.put("amountFen", data.path("amount").path("total").asString("0"));
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("回调解析失败", e);
            throw new BizException(ResultCode.BAD_REQUEST, "回调解析失败");
        }
    }

    /** 构造请求签名头 */
    private String buildAuthorization(String method, String path, String body) {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String message = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = sign(message);
        return SCHEMA + " mchid=\"" + props.getMchId() + "\",nonce_str=\"" + nonce
                + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + props.getSerialNo()
                + "\",signature=\"" + signature + "\"";
    }

    private String sign(String message) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey());
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new BizException(ResultCode.ERROR, "微信支付签名失败");
        }
    }

    private boolean verify(String message, String signature) {
        if (props.getPlatformPublicKeyPath() == null || props.getPlatformPublicKeyPath().isBlank()) {
            // 未配置平台公钥时跳过验签，仅用于本地联调，生产必须配置
            log.warn("未配置微信支付平台公钥，已跳过回调验签（生产环境必须配置）");
            return true;
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(platformPublicKey());
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            log.error("回调验签异常", e);
            return false;
        }
    }

    /** AES-256-GCM 解密回调 resource */
    private String decrypt(String ciphertext, String nonce, String associatedData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec key = new SecretKeySpec(props.getApiV3Key().getBytes(StandardCharsets.UTF_8), "AES");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        if (associatedData != null && !associatedData.isEmpty()) {
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        }
        byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
        return new String(plain, StandardCharsets.UTF_8);
    }

    private PrivateKey privateKey() throws Exception {
        if (privateKey == null) {
            synchronized (this) {
                if (privateKey == null) {
                    String pem = Files.readString(Path.of(props.getPrivateKeyPath()), StandardCharsets.UTF_8);
                    String base64 = pem.replaceAll("-----BEGIN[^-]+-----", "")
                            .replaceAll("-----END[^-]+-----", "")
                            .replaceAll("\\s", "");
                    privateKey = KeyFactory.getInstance("RSA")
                            .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
                }
            }
        }
        return privateKey;
    }

    private PublicKey platformPublicKey() throws Exception {
        if (platformPublicKey == null) {
            synchronized (this) {
                if (platformPublicKey == null) {
                    String pem = Files.readString(Path.of(props.getPlatformPublicKeyPath()), StandardCharsets.UTF_8);
                    String base64 = pem.replaceAll("-----BEGIN[^-]+-----", "")
                            .replaceAll("-----END[^-]+-----", "")
                            .replaceAll("\\s", "");
                    platformPublicKey = KeyFactory.getInstance("RSA")
                            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
                }
            }
        }
        return platformPublicKey;
    }

    private void requireConfigured() {
        if (!props.isConfigured()) {
            throw new BizException(ResultCode.STATE_CONFLICT,
                    "微信支付未配置，请设置 app.pay.wechat.* 或改用 mock 通道");
        }
    }
}
