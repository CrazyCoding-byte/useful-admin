package cn.poile.ucs.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 认证中心服务
 * @author: yaohw
 * @create: 2019-09-25 16:48
 **/
@SpringBootApplication(exclude = {})
@EnableDiscoveryClient
@MapperScan({"cn.poile.ucs.auth.mapper"})
@ComponentScan(
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.yzx\\.common\\.config\\..*"
    )
)
@EnableFeignClients(basePackages = "com.yzx.apiclient.api")
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}