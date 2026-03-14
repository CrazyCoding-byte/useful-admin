package com.yzx.common.schedule;

import com.yzx.common.service.IMqMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @className: MessageRetryService
 * @author: yzx
 * @date: 2026/3/14 19:14
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRetryService {
    private final IMqMessageService mqMessageService;
    private final RabbitTemplate rabbitTemplate;
    private final ApplicationContext applicationContext;
    //缓存 bizType -> handler 映射
    private final Map<String,LocalTaskHandler> handlerMap=new ConcurrentHashMap<>();



    private LocalTaskHandler getHandler(String bizType){

    }
}
