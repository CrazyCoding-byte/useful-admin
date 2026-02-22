package com.yzx.usefulagent.utils;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * @className: SensitiveDataMasker
 * @author: yzx
 * @date: 2026/2/22 21:36
 * @Version: 1.0
 * @description:
 */
@Component
public class SensitiveDataMasker {
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    // 身份证正则
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");
    // 收货地址正则
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(省|市|区|县|街道|路|号|小区|栋|单元)\\S{2,20}");

    /**
     * 脱敏处理
     */
    public String mask(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        // 手机号脱敏：138****1234
        content = PHONE_PATTERN.matcher(content).replaceAll(matchResult -> {
            String phone = matchResult.group();
            return phone.substring(0, 3) + "****" + phone.substring(7);
        });
        // 身份证脱敏：110***********1234
        content = ID_CARD_PATTERN.matcher(content).replaceAll(matchResult -> {
            String idCard = matchResult.group();
            return idCard.substring(0, 3) + "***********" + idCard.substring(14);
        });
        return content;
    }
}
