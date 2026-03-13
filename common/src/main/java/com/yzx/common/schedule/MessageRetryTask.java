package com.yzx.common.schedule;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mqlocalmessage.MqMessage;
import com.yzx.common.service.IMqMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
        try{
            //2.分页查询失败消息
            List<MqMessage> failListMessage=mqMessageService.lambdaQuery().eq(MqMessage::getStatus, MessageStatusEnum.FAIL.getCode())
                    .orderByAsc(MqMessage::getCreateTime).page(new Page<>(1,PAGE_SIZE)).getRecords();
        }
    }
}
