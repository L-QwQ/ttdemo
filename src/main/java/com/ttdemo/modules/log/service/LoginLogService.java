package com.ttdemo.modules.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.web.RequestUtils;
import com.ttdemo.modules.log.entity.SysLoginLog;
import com.ttdemo.modules.log.mapper.SysLoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 登录/注册审计日志。写日志失败不影响主流程。
 */
@Service
public class LoginLogService {

    private static final Logger log = LoggerFactory.getLogger(LoginLogService.class);

    private final SysLoginLogMapper loginLogMapper;

    public LoginLogService(SysLoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    public void record(Long userId, String phone, String type, boolean success, String message,
                       HttpServletRequest request) {
        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setUserId(userId);
            entity.setPhone(phone);
            entity.setType(type);
            entity.setSuccess(success ? 1 : 0);
            entity.setMessage(message);
            entity.setIp(RequestUtils.getClientIp(request));
            entity.setUserAgent(RequestUtils.getUserAgent(request));
            loginLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写入登录日志失败: {}", e.getMessage());
        }
    }

    public PageResult<SysLoginLog> page(long page, long size, String phone, Integer success) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .like(phone != null && !phone.isBlank(), SysLoginLog::getPhone, phone)
                .eq(success != null, SysLoginLog::getSuccess, success)
                .orderByDesc(SysLoginLog::getId);
        Page<SysLoginLog> result = loginLogMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }
}
