package com.ttdemo.modules.system.service;

import com.ttdemo.common.web.RequestUtils;
import com.ttdemo.modules.system.entity.SysAuditLog;
import com.ttdemo.modules.system.mapper.SysAuditLogMapper;
import com.ttdemo.security.LoginUser;
import com.ttdemo.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 操作审计：审核、下架、调账、结算撤销、开奖更正必须留痕。
 * 审计写入失败不影响主业务（仅告警日志）。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final SysAuditLogMapper mapper;

    public AuditLogService(SysAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(String module, String action, String bizType, Long bizId,
                       String before, String after, String remark) {
        try {
            SysAuditLog entity = new SysAuditLog();
            LoginUser user = SecurityUtils.currentUserOrNull();
            entity.setOperatorId(user == null ? 0L : user.id());
            entity.setOperatorName(user == null ? "SYSTEM" : user.phone());
            entity.setModule(module);
            entity.setAction(action);
            entity.setBizType(bizType);
            entity.setBizId(bizId);
            entity.setBeforeValue(before);
            entity.setAfterValue(after);
            entity.setRemark(remark);
            entity.setClientIp(RequestUtils.currentClientIp());
            mapper.insert(entity);
        } catch (Exception e) {
            log.warn("审计日志写入失败: module={}, action={}, bizId={}", module, action, bizId, e);
        }
    }
}
