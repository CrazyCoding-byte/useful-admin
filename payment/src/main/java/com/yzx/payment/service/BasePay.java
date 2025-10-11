package com.yzx.payment.service;

import com.google.gson.Gson;
import com.yzx.apiclient.api.OrderFeignService;
import com.yzx.model.AjaxResult;
import com.yzx.model.order.OrderEntity;
import com.yzx.payment.config.WxPayConfig;
import com.yzx.payment.enums.wxpay.WxApiType;
import com.yzx.payment.enums.wxpay.WxNotifyType;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @className: BasePay
 * @author: yzx
 * @date: 2025/10/11 16:40
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Component
public abstract class BasePay {
    @Resource
    private WxPayConfig wxPayConfig;
    @Autowired
    private OrderFeignService feignService;
    @Resource
    private CloseableHttpClient wxPayClient;

    /**
     * 创建订单，调用Native支付接口
     * @param orderSn
     * @return code_url 和 订单号
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult nativePay(String orderSn) throws Exception {
        log.info("获取订单");
        //远程调用拿到订单的信息
        AjaxResult data = feignService.getOrderInfo(orderSn);
        OrderEntity orderInfo = (OrderEntity) data.get("data");
        if (Objects.isNull(data)) return AjaxResult.error("订单不存在");
        String codeUrl = orderInfo.getCodeUrl();
        if (orderInfo != null && !StringUtils.isEmpty(codeUrl)) {
            log.info("订单已存在，二维码已保存");
            //返回二维码
            Map<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderSn());
            return AjaxResult.success(map);
        }
        log.info("调用统一下单API");
        //调用统一下单API
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(WxApiType.NATIVE_PAY.getType()));
        // 请求body参数
        Gson gson = new Gson();
        Map paramsMap = new HashMap();
        paramsMap.put("appid", wxPayConfig.getAppid());
        paramsMap.put("mchid", wxPayConfig.getMchId());
        paramsMap.put("description", orderInfo.getNote());
        paramsMap.put("out_trade_no", orderInfo.getOrderSn());
        paramsMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.NATIVE_NOTIFY.getType()));
        Map amountMap = new HashMap();
        amountMap.put("total", orderInfo.getTotalAmount());
        amountMap.put("currency", "CNY");
        paramsMap.put("amount", amountMap);

        //将参数转换成json字符串
        String jsonParams = gson.toJson(paramsMap);
        log.info("请求参数 ===> {}" + jsonParams);

        StringEntity entity = new StringEntity(jsonParams, "utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpPost);

        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());//响应体
            int statusCode = response.getStatusLine().getStatusCode();//响应状态码
            if (statusCode == 200) { //处理成功
                log.info("成功, 返回结果 = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("成功");
            } else {
                log.info("Native下单失败,响应码 = " + statusCode + ",返回结果 = " + bodyAsString);
                throw new IOException("request failed");
            }

            //响应结果
            Map<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);
            //二维码
            codeUrl = resultMap.get("code_url");

            //保存二维码
            String orderNo = orderInfo.getOrderSn();
            feignService.updateOrder(orderNo, codeUrl);

            //返回二维码
            Map<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderSn());
            return AjaxResult.success(map);
        } finally {
            response.close();
        }
    }
}
