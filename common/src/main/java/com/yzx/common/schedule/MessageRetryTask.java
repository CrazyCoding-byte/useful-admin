package com.yzx.common.schedule;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mqlocalmessage.MqMessage;
import com.yzx.common.service.IMqMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @className: MessageRetryTask
 * @author: yzx
 * @date: 2026/3/13 21:31
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRetryTask {
    private final IMqMessageService mqMessageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final MessageRetryService messageRetryService;
    @Value("${spring.application.name}")
    private String appName;
    //分布式锁key
    //每次分页查询100条
    private static final int PAGE_SIZE = 100;
    //最大重试次数
    private static final int MAX_RETRY = 3;

    /**
     * 定时任务
     */
    @Scheduled(cron = "0/05 * * * * ?")
    public void retryTask() {
        log.info("定时任务开启");
        String lockKey = "mq:message:retry:lock:" + appName;
        Boolean lock = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "running", 30, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(lock)) {
            log.info("【重试任务】其他实例已执行，跳过");
            return;
        }
        try {
            //2.分页查询失败消息
            Page<MqMessage> page = new Page<>(1, PAGE_SIZE, false);
            Page<MqMessage> resultPage = mqMessageService.lambdaQuery()
                    .eq(MqMessage::getAppName, appName)
                    .eq(MqMessage::getStatus, MessageStatusEnum.FAIL.getCode())
                    .lt(MqMessage::getRetryCount, MAX_RETRY)
                    .orderByAsc(MqMessage::getLastRetryTime)
                    .orderByAsc(MqMessage::getCreateTime)
                    .page(page);
            List<MqMessage> failListMessage = resultPage.getRecords();
            if (CollectionUtils.isEmpty(failListMessage)) {
                log.debug("服务[{}]没有需要重试的消息", appName);
                return;
            }
            log.info("【重试任务】服务[{}]有{}条消息需要重试", appName, failListMessage.size());
            for (MqMessage mqMessage : failListMessage) {
                messageRetryService.asyncRetry(mqMessage);
            }
        } catch (Exception e){
            log.error("【重试任务】服务[{}]重试失败", appName, e);
        }
        finally {
            // 4. 释放分布式锁
            stringRedisTemplate.delete(lockKey);

        }
    }


}
