package top.flyingjack.cashier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.flyingjack.cashier.entity.Order;
import top.flyingjack.cashier.service.OrderService;
import top.flyingjack.common.dto.ApiRes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiRes<Integer>> createOrder(
            @RequestParam("me_id") int meId, @RequestParam("selling_price") BigDecimal sellingPrice,
            @RequestParam(value = "selling_time", required = false) Instant sellingTime,
            @RequestParam String remark) {
        Instant time = sellingTime != null ? sellingTime : Instant.now();
        int id = orderService.insertOrder(meId, sellingPrice, remark, time);
        return ResponseEntity.ok(ApiRes.success(id));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiRes<List<Integer>>> batchCreateOrder(@RequestBody List<Order> orders) {
        List<Integer> ids = orderService.insertOrderBatch(orders);
        return ResponseEntity.ok(ApiRes.success(ids));
    }

    @GetMapping("/range")
    public ResponseEntity<ApiRes<Map<String, Object>>> getOrdersByDateRange(
            @RequestParam Instant start, @RequestParam Instant end,
            @RequestParam int limit, @RequestParam int offset) {
        if (limit > 999 || limit <= 0 || offset < 0) {
            return ResponseEntity.badRequest().body(ApiRes.fail(org.springframework.http.HttpStatus.BAD_REQUEST));
        }
        int count = orderService.getOrderCount(start, end);
        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        data.put("orders", orderService.getOrdersByPage(limit, offset, start, end));
        return ResponseEntity.ok(ApiRes.success(data));
    }

    @PutMapping("/return/{id}")
    public ResponseEntity<ApiRes<Void>> returningOrder(@PathVariable("id") int orderId) {
        orderService.returnOrder(orderId);
        return ResponseEntity.ok(ApiRes.success());
    }
}
