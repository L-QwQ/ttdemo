package com.ttdemo.common.api;

/**
 * 通用分页查询参数，作为 @ModelAttribute 绑定 query string。
 * page 从 1 开始，size 上限 100，防止一次性拉全表。
 */
public class PageQuery {

    private Long page = 1L;
    private Long size = 10L;

    public Long getPage() {
        return page == null || page < 1 ? 1L : page;
    }

    public void setPage(Long page) {
        this.page = page;
    }

    public Long getSize() {
        if (size == null || size < 1) {
            return 10L;
        }
        return Math.min(size, 100L);
    }

    public void setSize(Long size) {
        this.size = size;
    }

    /** 兼容需求文档的 pageNum 参数 */
    public Integer getPageNum() {
        return getPage().intValue();
    }

    public void setPageNum(Integer pageNum) {
        this.page = pageNum == null ? 1L : pageNum.longValue();
    }

    /** 兼容需求文档的 pageSize 参数（最大 50，与文档一致） */
    public Integer getPageSize() {
        return (int) Math.min(getSize(), 50L);
    }

    public void setPageSize(Integer pageSize) {
        this.size = pageSize == null ? 10L : pageSize.longValue();
    }

    public long offset() {
        return (getPage() - 1) * getSize();
    }
}
