package top.flyingjack.cashier.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import top.flyingjack.cashier.entity.Merchandise;
import top.flyingjack.cashier.entity.Order;
import top.flyingjack.cashier.entity.OrderListItemDto;
import top.flyingjack.cashier.mapper.OrderMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OrderService {
    // sanity bounds for client-supplied sellingTime, to catch unit/format mismatches
    // (e.g. an epoch-ms value misparsed as epoch-seconds) before they reach the database
    private static final Instant SELLING_TIME_FLOOR = Instant.parse("2000-01-01T00:00:00Z");

    private final OrderMapper orderMapper;
    private final WmsSecurityContext securityContext;
    private final MerchandiseService merchandiseService;

    public OrderService(OrderMapper orderMapper, WmsSecurityContext securityContext,
                         MerchandiseService merchandiseService) {
        this.orderMapper = orderMapper;
        this.securityContext = securityContext;
        this.merchandiseService = merchandiseService;
    }

    @Transactional
    public int insertOrder(int meId, BigDecimal sellingPrice, String remark, Instant sellingTime) {
        Assert.isTrue(meId > 0, "invalid merchandise id");
        validateNotSold(meId);
        Order order = new Order();
        order.setGroupId(securityContext.currentGroupId());
        order.setMeId(meId);
        order.setSellingPrice(sellingPrice);
        order.setRemark(remark);
        order.setSellingTime(sellingTime != null ? sellingTime : Instant.now());
        order.setReturned(false);
        orderMapper.insert(order);
        merchandiseService.markSold(meId, true);
        return order.getId();
    }

    @Transactional
    public List<Integer> insertOrderBatch(List<Order> orders) {
        Assert.notEmpty(orders, "orders cannot be empty");
        orders.forEach(o -> validateSellingTime(o.getSellingTime()));
        long distinctMeIds = orders.stream().map(Order::getMeId).distinct().count();
        Assert.isTrue(distinctMeIds == orders.size(), "duplicate merchandise id within batch");
        orders.forEach(o -> validateNotSold(o.getMeId()));

        int groupId = securityContext.currentGroupId();
        orders.forEach(o -> o.setGroupId(groupId));
        orderMapper.insertBatch(orders);
        orders.forEach(o -> merchandiseService.markSold(o.getMeId(), true));
        return orders.stream().map(Order::getId).toList();
    }

    private void validateNotSold(int meId) {
        Merchandise merchandise = merchandiseService.findById(meId);
        Assert.isTrue(merchandise != null, "merchandise not found: " + meId);
        Assert.isTrue(!merchandise.isSold(), "merchandise already sold: " + meId);
    }

    private void validateSellingTime(Instant sellingTime) {
        Assert.isTrue(sellingTime != null
                        && sellingTime.isAfter(SELLING_TIME_FLOOR)
                        && sellingTime.isBefore(Instant.now().plus(1, ChronoUnit.DAYS)),
                "sellingTime out of reasonable range: " + sellingTime);
    }

    public int getOrderCount(Instant start, Instant end) {
        return orderMapper.countByGroupAndDateRange(securityContext.currentGroupId(), start, end);
    }

    public List<OrderListItemDto> getOrdersByPage(int limit, int offset, Instant start, Instant end) {
        return orderMapper.findByGroupAndDateRangePaged(
                securityContext.currentGroupId(), start, end, limit, offset);
    }

    @Transactional
    public void returnOrder(int orderId) {
        int groupId = securityContext.currentGroupId();
        Integer meId = orderMapper.findMeIdById(orderId, groupId);
        Assert.isTrue(meId != null, "order not found: " + orderId);
        orderMapper.markReturned(orderId, groupId);
        merchandiseService.markSold(meId, false);
    }
}
