package com.ttdemo.common.util;

import java.util.regex.Pattern;

public final class PhoneUtils {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private PhoneUtils() {
    }

    public static boolean isValid(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    /** 138****8000，用于日志脱敏 */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
