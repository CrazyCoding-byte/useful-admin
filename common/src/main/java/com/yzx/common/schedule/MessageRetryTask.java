package com.yzx.common.schedule;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mqlocalmessage.MqMessage;
import com.yzx.common.service.IMqMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    //分布式锁key
    private static final String LOCK_KEY = "mq:message:retry:lock";
    //每次分页查询100条
    private static final int PAGE_SIZE=100;
    //最大重试次数
    private static final int MAX_RETRY=3;
    /**
     * 定时任务
     */
    @Scheduled(cron = "0/01 * * * * ?")
    public void retryTask(){
        Boolean lock = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "running", 30, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(lock)) {
            log.info("【重试任务】其他实例已执行，跳过");
            return;
        }
        try{
            //2.分页查询失败消息
            List<MqMessage> failListMessage=mqMessageService.lambdaQuery().eq(MqMessage::getStatus, MessageStatusEnum.FAIL.getCode())
                    .lt(MqMessage::getRetryCount,MAX_RETRY)
                    .last("limit "+PAGE_SIZE)
                    .list();
            if(CollectionUtils.isEmpty(failListMessage)){
                return;
            }
            for(MqMessage mqMessage:failListMessage){
                asyncRetryMessage(mqMessage);
            }
        }finally{
            // 4. 释放分布式锁
            stringRedisTemplate.delete(LOCK_KEY);
        }
    }
    @Async("messageRetryExecutor")
    public void asyncRetryMessage(MqMessage msg) {
        try {
            // MQ消息重试
            if (StringUtils.hasText(msg.getExchange())) {
                rabbitTemplate.convertAndSend(
                        msg.getExchange(),
                        msg.getRoutingKey(),
                        msg.getContent()
                );
            }
            // 本地任务重试（可自行扩展）
            else {
                log.info("【本地任务重试】bizType:{}，content:{}", msg.getBizType(), msg.getContent());
            }

            // 5. 重试成功 → 更新状态
            mqMessageService.lambdaUpdate()
                    .eq(MqMessage::getMsgId, msg.getMsgId())
                    .set(MqMessage::getStatus, MessageStatusEnum.SUCCESS.getCode())
                    .update();

        } catch (Exception e) {
            // 重试失败 → 累加重试次数
            log.error("【消息重试失败】msgId:{}", msg.getMsgId(), e);
            mqMessageService.incrementRetry(msg.getMsgId());
        }
    }
}
