package com.ttdemo.config;

import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 首次启动自动创建管理员账号（幂等）。生产环境请改掉默认密码。
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserService userService;
    private final String adminPhone;
    private final String adminPassword;

    public AdminAccountInitializer(UserService userService,
                                   @Value("${app.init.admin-phone:13800000000}") String adminPhone,
                                   @Value("${app.init.admin-password:admin123456}") String adminPassword) {
        this.userService = userService;
        this.adminPhone = adminPhone;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        SysUser existing = userService.getByPhone(adminPhone);
        if (existing != null) {
            if (!existing.isAdmin()) {
                log.warn("手机号 {} 已存在但不是管理员，如需后台权限请手动把 role 改为 ADMIN", adminPhone);
            }
            return;
        }
        userService.create(adminPhone, adminPassword, "超级管理员", SysUser.ROLE_ADMIN);
        log.warn("已创建默认管理员：账号 {} 密码 {} —— 请立即登录后台修改密码", adminPhone, adminPassword);
    }
}
