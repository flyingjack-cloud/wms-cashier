package top.flyingjack.cashier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import top.flyingjack.common.config.anotation.EnableGlobalCache;
import top.flyingjack.common.config.anotation.EnableGlobalException;
import top.flyingjack.common.config.anotation.EnableGlobalI18n;
import top.flyingjack.common.config.anotation.EnableGlobalJackson;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableGlobalException
@EnableGlobalI18n
@EnableGlobalJackson
@EnableGlobalCache
public class CashierApplication {
    public static void main(String[] args) {
        SpringApplication.run(CashierApplication.class, args);
    }
}
