package com.ttdemo.modules.feedback.dto;

import com.ttdemo.common.api.PageQuery;

/**
 * 反馈列表查询条件（下推到 SQL）
 */
public class FeedbackQuery extends PageQuery {

    private String keyword;
    private String type;
    private Integer status;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
