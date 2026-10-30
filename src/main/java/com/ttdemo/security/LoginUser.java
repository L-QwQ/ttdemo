package com.ttdemo.security;

import java.security.Principal;

/**
 * 认证通过后放入 SecurityContext 的主体信息
 */
public record LoginUser(Long id, String phone, String nickname, String role) implements Principal {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    @Override
    public String getName() {
        return phone;
    }
}
