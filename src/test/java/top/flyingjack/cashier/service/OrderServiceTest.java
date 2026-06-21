package top.flyingjack.cashier.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.flyingjack.cashier.entity.Order;
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
}
