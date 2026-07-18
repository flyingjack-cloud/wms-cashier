package top.flyingjack.cashier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.flyingjack.cashier.entity.OrderExtraDto;
import top.flyingjack.cashier.entity.OrderExtraTemplateDto;
import top.flyingjack.cashier.entity.OrderExtraTemplateEnabledReq;
import top.flyingjack.cashier.entity.OrderExtraTemplateReq;
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
    public ResponseEntity<ApiRes<List<OrderExtraTemplateDto>>> getTemplates(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ResponseEntity.ok(ApiRes.success(orderExtraService.getTemplates(includeDisabled)));
    }

    @PostMapping("/order-extra/templates")
    public ResponseEntity<ApiRes<OrderExtraTemplateDto>> createTemplate(
            @RequestBody OrderExtraTemplateReq req) {
        return ResponseEntity.ok(ApiRes.success(orderExtraService.createTemplate(req)));
    }

    @PutMapping("/order-extra/templates/{code}")
    public ResponseEntity<ApiRes<OrderExtraTemplateDto>> updateTemplate(
            @PathVariable String code, @RequestBody OrderExtraTemplateReq req) {
        return ResponseEntity.ok(ApiRes.success(orderExtraService.updateTemplate(code, req)));
    }

    @DeleteMapping("/order-extra/templates/{code}")
    public ResponseEntity<ApiRes<Void>> deleteTemplate(@PathVariable String code) {
        orderExtraService.disableTemplate(code);
        return ResponseEntity.ok(ApiRes.success());
    }

    @PutMapping("/order-extra/templates/{code}/enabled")
    public ResponseEntity<ApiRes<OrderExtraTemplateDto>> setTemplateEnabled(
            @PathVariable String code, @RequestBody OrderExtraTemplateEnabledReq req) {
        return ResponseEntity.ok(ApiRes.success(
                orderExtraService.setTemplateEnabled(code, req.isEnabled())));
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
