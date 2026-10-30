package com.ttdemo.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ttdemo.modules.system.entity.SysConfig;
import com.ttdemo.modules.system.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;

/**
 * 系统配置中心：启动加载全量到内存，后台修改后热更新。
 * 业务代码一律读配置，不写死价格/阈值。
 */
@Service
public class SysConfigService {

    private static final Logger log = LoggerFactory.getLogger(SysConfigService.class);

    private final SysConfigMapper mapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SysConfigService(SysConfigMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void load() {
        cache.clear();
        List<SysConfig> all = mapper.selectList(new LambdaQueryWrapper<>());
        for (SysConfig c : all) {
            cache.put(c.getConfigKey(), c.getConfigValue());
        }
        log.info("系统配置加载完成，共 {} 项", cache.size());
    }

    public String getString(String key, String defaultValue) {
        String value = cache.get(key);
        return value == null ? defaultValue : value;
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getString(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(getString(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public BigDecimal getDecimal(String key, String defaultValue) {
        try {
            return new BigDecimal(getString(key, defaultValue).trim());
        } catch (NumberFormatException e) {
            return new BigDecimal(defaultValue);
        }
    }

    /** 兼容 true/false 与 1/0、on/off、yes/no 三种写法（运营在后台常填 0/1） */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = cache.get(key);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        if ("1".equals(normalized) || "true".equals(normalized) || "on".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "off".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return defaultValue;
    }

    public List<SysConfig> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getConfigKey));
    }

    /** 后台修改配置，仅 editable=1 可改 */
    public void update(String key, String value, Long operatorId) {
        SysConfig existing = mapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "配置项不存在: " + key);
        }
        if (existing.getEditable() != null && existing.getEditable() == 0) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该项需发版修改");
        }
        existing.setConfigValue(value);
        existing.setUpdatedBy(operatorId);
        mapper.updateById(existing);
        cache.put(key, value);
    }

    public void refresh() {
        load();
    }
}
