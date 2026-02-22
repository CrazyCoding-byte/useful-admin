package com.yzx.usefulagent.utils;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @className: SensitiveWordFilter
 * @author: yzx
 * @date: 2026/2/22 22:14
 * @Version: 1.0
 * @description:
 */
@Component
public class SensitiveWordFilter {
    private static final Set<String> words = Set.of("敏感", "色情", "暴力");

    public boolean contains(String text) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
