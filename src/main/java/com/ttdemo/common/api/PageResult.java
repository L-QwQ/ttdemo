package com.ttdemo.common.api;

import java.util.List;

/**
 * 统一分页结果（对齐需求文档 7.1.2 的 list/total/pageNum/pageSize/hasNext）
 */
public record PageResult<T>(List<T> list, long total, int pageNum, int pageSize, boolean hasNext) {

    public static <T> PageResult<T> of(long total, long page, long size, List<T> list) {
        int pn = (int) Math.max(1, page);
        int ps = (int) Math.max(1, size);
        boolean hasNext = (long) pn * ps < total;
        return new PageResult<>(list == null ? List.of() : list, total, pn, ps, hasNext);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return of(0, page, size, List.of());
    }
}
