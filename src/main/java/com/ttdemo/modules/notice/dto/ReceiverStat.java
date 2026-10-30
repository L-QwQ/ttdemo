package com.ttdemo.modules.notice.dto;

/**
 * 通知接收情况聚合（一次 group by 查询，避免列表页 N+1）
 */
public class ReceiverStat {

    private Long noticeId;
    private Long total;
    private Long readCount;

    public Long getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(Long noticeId) {
        this.noticeId = noticeId;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Long getReadCount() {
        return readCount;
    }

    public void setReadCount(Long readCount) {
        this.readCount = readCount;
    }
}
