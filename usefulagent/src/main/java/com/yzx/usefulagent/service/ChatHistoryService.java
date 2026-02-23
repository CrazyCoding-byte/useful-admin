package com.yzx.usefulagent.service;

import java.util.List;
import java.util.Map;

public interface ChatHistoryService {
    List<Map<String, String>> getUserHistory(String userId, int limit);
}