package top.flyingjack.cashier.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.flyingjack.cashier.entity.Category;
import top.flyingjack.cashier.entity.MerchandiseWithCategoryDto;
import top.flyingjack.cashier.entity.Order;
import top.flyingjack.cashier.entity.OrderListItemDto;
import top.flyingjack.cashier.mapper.OrderMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderMapper orderMapper;
    @Mock WmsSecurityContext securityContext;
    @InjectMocks OrderService orderService;

    @Test
    void insertOrder_returnsGeneratedId() {
        when(securityContext.currentGroupId()).thenReturn(1);
        doAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(42);
            return null;
        }).when(orderMapper).insert(any());

        int id = orderService.insertOrder(10, new BigDecimal("999"), "测试备注", Instant.now());

        assertThat(id).isEqualTo(42);
    }

    @Test
    void returnOrder_setsReturnedFlag() {
        when(securityContext.currentGroupId()).thenReturn(1);
        orderService.returnOrder(1);
        verify(orderMapper).markReturned(eq(1), anyInt());
    }

    @Test
    void getOrderCount_delegatesToMapper() {
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        when(securityContext.currentGroupId()).thenReturn(1);
        when(orderMapper.countByGroupAndDateRange(1, start, end)).thenReturn(7);

        int count = orderService.getOrderCount(start, end);

        assertThat(count).isEqualTo(7);
    }

    @Test
    void getOrdersByPage_delegatesToMapper() {
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        OrderListItemDto order = new OrderListItemDto();
        when(securityContext.currentGroupId()).thenReturn(1);
        when(orderMapper.findByGroupAndDateRangePaged(1, start, end, 20, 0))
                .thenReturn(List.of(order));

        List<OrderListItemDto> orders = orderService.getOrdersByPage(20, 0, start, end);

        assertThat(orders).containsExactly(order);
    }

    @Test
    void getOrdersByPage_includesNestedMerchandise() {
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        MerchandiseWithCategoryDto merchandise = new MerchandiseWithCategoryDto();
        merchandise.setCost(new BigDecimal("100.00"));
        merchandise.setImei("IMEI001");
        OrderListItemDto order = new OrderListItemDto();
        order.setMerchandise(merchandise);
        when(securityContext.currentGroupId()).thenReturn(1);
        when(orderMapper.findByGroupAndDateRangePaged(1, start, end, 20, 0))
                .thenReturn(List.of(order));

        List<OrderListItemDto> orders = orderService.getOrdersByPage(20, 0, start, end);

        assertThat(orders.get(0).getMerchandise().getCost()).isEqualByComparingTo("100.00");
        assertThat(orders.get(0).getMerchandise().getImei()).isEqualTo("IMEI001");
    }

    @Test
    void getOrdersByPage_includesNestedCategoryWithinMerchandise() {
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        Category category = new Category();
        category.setName("手机");
        MerchandiseWithCategoryDto merchandise = new MerchandiseWithCategoryDto();
        merchandise.setCategory(category);
        OrderListItemDto order = new OrderListItemDto();
        order.setMerchandise(merchandise);
        when(securityContext.currentGroupId()).thenReturn(1);
        when(orderMapper.findByGroupAndDateRangePaged(1, start, end, 20, 0))
                .thenReturn(List.of(order));

        List<OrderListItemDto> orders = orderService.getOrdersByPage(20, 0, start, end);

        assertThat(orders.get(0).getMerchandise().getCategory().getName()).isEqualTo("手机");
    }
}
