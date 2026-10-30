package com.ttdemo.modules.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ttdemo.modules.risk.entity.RiskSensitiveWord;
import com.ttdemo.modules.risk.mapper.RiskSensitiveWordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地敏感词与风控检测（替代云内容安全，单机可用）。
 * 词库来自 risk_sensitive_word，启动加载，后台新增后 refresh()。
 */
@Service
public class RiskService {

    private static final Logger log = LoggerFactory.getLogger(RiskService.class);

    /** 命中词 → level（2 拦截，1 提示） */
    private final Map<String, Integer> words = new ConcurrentHashMap<>();

    private final RiskSensitiveWordMapper wordMapper;

    public RiskService(RiskSensitiveWordMapper wordMapper) {
        this.wordMapper = wordMapper;
    }

    @PostConstruct
    public void load() {
        refresh();
    }

    public void refresh() {
        words.clear();
        try {
            List<RiskSensitiveWord> list = wordMapper.selectList(
                    new LambdaQueryWrapper<RiskSensitiveWord>()
                            .eq(RiskSensitiveWord::getStatus, 1));
            for (RiskSensitiveWord w : list) {
                words.put(w.getWord().toLowerCase(Locale.ROOT), w.getLevel() == null ? 1 : w.getLevel());
            }
            log.info("敏感词库加载完成，共 {} 条", words.size());
        } catch (Exception e) {
            log.warn("敏感词库加载失败，降级为空词库", e);
        }
    }

    /**
     * 扫描文本，返回命中的拦截级词（逗号分隔）；为空表示通过。
     */
    public String scanBlocking(String... texts) {
        List<String> hits = new ArrayList<>();
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Integer> entry : words.entrySet()) {
                if (entry.getValue() >= 2 && lower.contains(entry.getKey())) {
                    hits.add(entry.getKey());
                }
            }
        }
        return hits.stream().distinct().reduce((a, b) -> a + "," + b).orElse("");
    }

    /** 扫描全部命中词（含提示级），用于审核台展示 */
    public String scanAll(String... texts) {
        List<String> hits = new ArrayList<>();
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            for (String word : words.keySet()) {
                if (lower.contains(word)) {
                    hits.add(word);
                }
            }
        }
        return hits.stream().distinct().reduce((a, b) -> a + "," + b).orElse("");
    }

    public List<String> wordList() {
        return new ArrayList<>(words.keySet());
    }
}
