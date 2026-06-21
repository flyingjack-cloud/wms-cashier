package top.flyingjack.cashier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.flyingjack.cashier.entity.Order;
import top.flyingjack.cashier.service.OrderService;
import top.flyingjack.common.dto.ApiRes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/order")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/")
    public ResponseEntity<ApiRes<Integer>> createOrder(
            @RequestParam("me_id") int meId, @RequestParam("selling_price") BigDecimal sellingPrice,
            @RequestParam(value = "selling_time", required = false) Long sellingTime,
            @RequestParam String remark) {
        Instant time = sellingTime != null ? Instant.ofEpochMilli(sellingTime) : Instant.now();
        int id = orderService.insertOrder(meId, sellingPrice, remark, time);
        return ResponseEntity.ok(ApiRes.success(id));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiRes<Void>> batchCreateOrder(@RequestBody List<Order> orders) {
        orderService.insertOrderBatch(orders);
        return ResponseEntity.ok(ApiRes.success());
    }

    @GetMapping("/range")
    public ResponseEntity<ApiRes<List<Order>>> getOrdersByDateRange(
            @RequestParam long start, @RequestParam long end) {
        List<Order> orders = orderService.getOrdersByDateRange(
                Instant.ofEpochMilli(start), Instant.ofEpochMilli(end));
        return ResponseEntity.ok(ApiRes.success(orders));
    }

    @PutMapping("/return/{id}")
    public ResponseEntity<ApiRes<Void>> returningOrder(@PathVariable("id") int orderId) {
        orderService.returnOrder(orderId);
        return ResponseEntity.ok(ApiRes.success());
    }
}
