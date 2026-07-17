package top.flyingjack.cashier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.flyingjack.cashier.entity.OrderExtraDto;
import top.flyingjack.cashier.entity.OrderExtraTemplateDto;
import top.flyingjack.cashier.service.OrderExtraService;
import top.flyingjack.common.dto.ApiRes;

import java.util.List;
import java.util.Map;

@RestController
public class OrderExtraController {
    private final OrderExtraService orderExtraService;

    public OrderExtraController(OrderExtraService orderExtraService) {
        this.orderExtraService = orderExtraService;
    }

    @GetMapping("/order-extra/templates")
    public ResponseEntity<ApiRes<List<OrderExtraTemplateDto>>> getTemplates() {
        return ResponseEntity.ok(ApiRes.success(orderExtraService.getTemplates(false)));
    }

    @GetMapping("/order-extra/templates/{code}")
    public ResponseEntity<ApiRes<OrderExtraTemplateDto>> getTemplate(@PathVariable String code) {
        return ResponseEntity.ok(ApiRes.success(orderExtraService.getTemplate(code)));
    }

    @PutMapping("/order/{orderId}/extra/{templateCode}")
    public ResponseEntity<ApiRes<Void>> saveExtra(@PathVariable int orderId, @PathVariable String templateCode,
                                                  @RequestBody Map<String, Object> payload) {
        orderExtraService.saveExtra(orderId, templateCode, payload);
        return ResponseEntity.ok(ApiRes.success());
    }

    @GetMapping("/order/{orderId}/extra")
    public ResponseEntity<ApiRes<List<OrderExtraDto>>> getExtras(@PathVariable int orderId) {
        return ResponseEntity.ok(ApiRes.success(orderExtraService.getExtras(orderId)));
    }

    @GetMapping("/order/{orderId}/extra/{templateCode}")
    public ResponseEntity<ApiRes<OrderExtraDto>> getExtra(@PathVariable int orderId,
                                                         @PathVariable String templateCode) {
        return ResponseEntity.ok(ApiRes.success(orderExtraService.getExtra(orderId, templateCode)));
    }
}
