package com.yzx.wms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients("com.yzx.apiclient")
@ComponentScan(basePackages = {"com.yzx.wms", "com.yzx.common", "com.yzx.model", "com.yzx.apiclient"})
@MapperScan({"com.yzx.wms.mapper", "com.yzx.common.mapper"})
public class WmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }

}
