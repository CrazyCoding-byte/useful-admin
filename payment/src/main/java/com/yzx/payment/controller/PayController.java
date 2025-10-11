package com.yzx.payment.controller;

import com.google.gson.Gson;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.yzx.payment.utils.HttpUtils;
import com.yzx.payment.utils.WechatPay2ValidatorForRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @className: PayController
 * @author: yzx
 * @date: 2025/10/11 16:44
 * @Version: 1.0
 * @description:
 */
@RestController
@Slf4j
@Controller("pay")
public class PayController {


    @Resource
    private Verifier verifier;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 支付通知
     * 微信支付通过支付通知接口将用户支付成功消息通知给商户
     */
    @PostMapping("/native/notify")
    public String nativeNotify(HttpServletRequest request, HttpServletResponse response) {
        Gson gson = new Gson();
        Map<String, String> map = new HashMap<>();//应答对象
        try {
            //处理通知参数
            String body = HttpUtils.readData(request);
            Map<String, Object> bodyMap = gson.fromJson(body, HashMap.class);
            String requestId = (String) bodyMap.get("id");
            log.info("支付通知的id ===> {}", requestId);
            //log.info("支付通知的完整数据 ===> {}", body);
            //签名的验证
            WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest
                    = new WechatPay2ValidatorForRequest(verifier, requestId, body);
            if (!wechatPay2ValidatorForRequest.validate(request)) {
                log.error("通知验签失败");
                //失败应答
                response.setStatus(500);
                map.put("code", "ERROR");
                map.put("message", "通知验签失败");
                return gson.toJson(map);
            }
            log.info("通知验签成功");
            //处理订单 todo 发送消息给mq 实际扣库存 并且修改订单位已支付
            rabbitTemplate.convertAndSend();
            //成功应答
            response.setStatus(200);
            map.put("code", "SUCCESS");
            map.put("message", "成功");
            return gson.toJson(map);

        } catch (Exception e) {
            e.printStackTrace();
            //失败应答
            response.setStatus(500);
            map.put("code", "ERROR");
            map.put("message", "失败");
            return gson.toJson(map);
        }

    }

}
