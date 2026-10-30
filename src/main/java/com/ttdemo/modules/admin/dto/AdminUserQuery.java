package com.ttdemo.modules.admin.dto;

import com.ttdemo.common.api.PageQuery;

/**
 * 后台用户列表查询条件（条件全部下推到 SQL）
 */
public class AdminUserQuery extends PageQuery {

    private String keyword;
    private Integer status;
    private String role;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
