package top.flyingjack.cashier.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import top.flyingjack.common.dto.ApiRes;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {
    @GetMapping("/internal/users/by-phone")
    ApiRes<Long> getUserIdByPhone(@RequestParam("phone") String phone);
}
