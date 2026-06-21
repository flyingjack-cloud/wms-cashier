package top.flyingjack.cashier.service;

import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import top.flyingjack.cashier.entity.Order;
import top.flyingjack.cashier.mapper.OrderMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class OrderService {
    private final OrderMapper orderMapper;
    private final WmsSecurityContext securityContext;

    public OrderService(OrderMapper orderMapper, WmsSecurityContext securityContext) {
        this.orderMapper = orderMapper;
        this.securityContext = securityContext;
    }

    public int insertOrder(int meId, BigDecimal sellingPrice, String remark, Instant sellingTime) {
        Assert.isTrue(meId > 0, "invalid merchandise id");
        Order order = new Order();
        order.setGroupId(securityContext.currentGroupId());
        order.setMeId(meId);
        order.setSellingPrice(sellingPrice);
        order.setRemark(remark);
        order.setSellingTime(sellingTime != null ? sellingTime : Instant.now());
        order.setReturned(false);
        orderMapper.insert(order);
        return order.getId();
    }

    public void insertOrderBatch(List<Order> orders) {
        Assert.notEmpty(orders, "orders cannot be empty");
        int groupId = securityContext.currentGroupId();
        orders.forEach(o -> o.setGroupId(groupId));
        orderMapper.insertBatch(orders);
    }

    public List<Order> getOrdersByDateRange(Instant start, Instant end) {
        return orderMapper.findByGroupAndDateRange(securityContext.currentGroupId(), start, end);
    }

    public void returnOrder(int orderId) {
        orderMapper.markReturned(orderId);
    }
}
