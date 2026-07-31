package com.yzx.coupon;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.yzx.apiclient.api")
@ComponentScan(basePackages = {"com.yzx.coupon", "com.yzx.common", "com.yzx.model", "com.yzx.apiclient.api"})
@MapperScan({"com.yzx.coupon.mapper", "com.yzx.common.mapper"})
@EnableDiscoveryClient
@EnableRabbit
public class CouponApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponApplication.class, args);
    }

}
