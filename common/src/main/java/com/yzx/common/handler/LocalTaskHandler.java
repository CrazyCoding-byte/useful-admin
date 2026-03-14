package com.yzx.common.handler;

/**
 * @className: LocalTaskHandler
 * @author: yzx
 * @date: 2026/3/14 19:18
 * @Version: 1.0
 * @description:
 */

public interface LocalTaskHandler {
    /**
     * 判断是否支持该业务类型
     * @Param bizType
     * @return
     */
    boolean support(String bizType);

    /**
     * 执行业务处理
     * @Param content 消息内容(JSON格式)
     * @Return true 成功,false 失败
     */
    boolean handler(String content);
}
