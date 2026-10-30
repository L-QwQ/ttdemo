package com.ttdemo.common.util;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;

/**
 * 大陆 18 位身份证校验工具（GB 11643-1999）。
 *
 * <p>之前只做正则匹配，像 {@code 110101111201011234}（出生年份 1112）这种号码也能入库，
 * 导致后台实名列表出现 914 岁的记录，审核时根本对不上人。这里补齐校验位与出生日期校验。</p>
 */
public final class IdCardUtils {

    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
    private static final String PATTERN = "^\\d{17}[\\dX]$";

    private IdCardUtils() {
    }

    /** 18 位格式 + 校验位 + 出生日期真实存在 */
    public static boolean isValid(String idCard) {
        if (idCard == null || !idCard.matches(PATTERN)) {
            return false;
        }
        String upper = idCard.toUpperCase();
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (upper.charAt(i) - '0') * WEIGHTS[i];
        }
        if (CHECK_CODES[sum % 11] != upper.charAt(17)) {
            return false;
        }
        try {
            birthDate(upper);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 解析出生日期，非法日期（如 1112-01-01、2026-02-30）抛 IllegalArgumentException */
    public static LocalDate birthDate(String idCard) {
        if (idCard == null || !idCard.matches(PATTERN)) {
            throw new IllegalArgumentException("身份证号格式不正确");
        }
        String upper = idCard.toUpperCase();
        try {
            LocalDate date = LocalDate.parse(upper.substring(6, 10) + "-"
                    + upper.substring(10, 12) + "-" + upper.substring(12, 14));
            int year = date.getYear();
            if (year < 1900 || year > LocalDate.now().getYear()) {
                throw new IllegalArgumentException("出生年份不合法：" + year);
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("出生日期不合法");
        }
    }

    /** 周岁年龄（未过生日则减一岁），与身份证出生日期严格对应 */
    public static int age(String idCard) {
        return Period.between(birthDate(idCard), LocalDate.now()).getYears();
    }
}
