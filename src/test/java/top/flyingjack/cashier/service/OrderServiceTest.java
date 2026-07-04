package top.flyingjack.cashier.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.flyingjack.cashier.entity.Category;
import top.flyingjack.cashier.entity.Merchandise;
import top.flyingjack.cashier.entity.MerchandiseWithCategoryDto;
import top.flyingjack.cashier.entity.Order;
import top.flyingjack.cashier.entity.OrderListItemDto;
import top.flyingjack.cashier.mapper.OrderMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderMapper orderMapper;
    @Mock WmsSecurityContext securityContext;
    @Mock MerchandiseService merchandiseService;
    @InjectMocks OrderService orderService;

    private static Merchandise merchandise(int id, boolean sold) {
        Merchandise m = new Merchandise();
        m.setId(id);
        m.setSold(sold);
        return m;
    }

    @Test
    void insertOrder_returnsGeneratedId() {
        when(securityContext.currentGroupId()).thenReturn(1);
        when(merchandiseService.findById(10)).thenReturn(merchandise(10, false));
        doAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(42);
            return null;
        }).when(orderMapper).insert(any());

        int id = orderService.insertOrder(10, new BigDecimal("999"), "测试备注", Instant.now());

        assertThat(id).isEqualTo(42);
    }

    @Test
    void insertOrder_marksMerchandiseSold() {
        when(securityContext.currentGroupId()).thenReturn(1);
        when(merchandiseService.findById(10)).thenReturn(merchandise(10, false));

        orderService.insertOrder(10, new BigDecimal("999"), "测试备注", Instant.now());

        verify(merchandiseService).markSold(10, true);
    }

    @Test
    void insertOrder_rejectsAlreadySoldMerchandise() {
        when(merchandiseService.findById(10)).thenReturn(merchandise(10, true));

        assertThatThrownBy(() -> orderService.insertOrder(10, new BigDecimal("999"), "测试备注", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void insertOrder_rejectsUnknownMerchandise() {
        when(merchandiseService.findById(10)).thenReturn(null);

        assertThatThrownBy(() -> orderService.insertOrder(10, new BigDecimal("999"), "测试备注", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void insertOrderBatch_setsGroupIdAndInsertsValidOrders() {
        when(securityContext.currentGroupId()).thenReturn(5);
        when(merchandiseService.findById(10)).thenReturn(merchandise(10, false));
        Order order = new Order();
        order.setMeId(10);
        order.setSellingPrice(new BigDecimal("100"));
        order.setSellingTime(Instant.now());
        order.setRemark("test");

        orderService.insertOrderBatch(List.of(order));

        assertThat(order.getGroupId()).isEqualTo(5);
        verify(orderMapper).insertBatch(List.of(order));
    }

    @Test
    void insertOrderBatch_marksAllMerchandiseSold() {
        when(securityContext.currentGroupId()).thenReturn(5);
        when(merchandiseService.findById(10)).thenReturn(merchandise(10, false));
        when(merchandiseService.findById(11)).thenReturn(merchandise(11, false));
        Order order1 = new Order();
        order1.setMeId(10);
        order1.setSellingPrice(new BigDecimal("100"));
        order1.setSellingTime(Instant.now());
        order1.setRemark("test");
        Order order2 = new Order();
        order2.setMeId(11);
        order2.setSellingPrice(new BigDecimal("100"));
        order2.setSellingTime(Instant.now());
        order2.setRemark("test");

        orderService.insertOrderBatch(List.of(order1, order2));

        verify(merchandiseService).markSold(10, true);
        verify(merchandiseService).markSold(11, true);
    }

    @Test
    void insertOrderBatch_rejectsDuplicateMeIdWithinBatch() {
        Order order1 = new Order();
        order1.setMeId(10);
        order1.setSellingPrice(new BigDecimal("100"));
        order1.setSellingTime(Instant.now());
        order1.setRemark("test");
        Order order2 = new Order();
        order2.setMeId(10);
        order2.setSellingPrice(new BigDecimal("100"));
        order2.setSellingTime(Instant.now());
        order2.setRemark("test");

        assertThatThrownBy(() -> orderService.insertOrderBatch(List.of(order1, order2)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderMapper, never()).insertBatch(any());
    }

    @Test
    void insertOrderBatch_rejectsAlreadySoldMerchandise() {
        when(merchandiseService.findById(10)).thenReturn(merchandise(10, true));
        Order order = new Order();
        order.setMeId(10);
        order.setSellingPrice(new BigDecimal("100"));
        order.setSellingTime(Instant.now());
        order.setRemark("test");

        assertThatThrownBy(() -> orderService.insertOrderBatch(List.of(order)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderMapper, never()).insertBatch(any());
    }

    @Test
    void insertOrderBatch_rejectsUnreasonableSellingTime() {
        Order order = new Order();
        order.setMeId(10);
        order.setSellingPrice(new BigDecimal("100"));
        // corrupted value matching the real-world bug: epoch-ms number misparsed as epoch-seconds
        order.setSellingTime(Instant.ofEpochSecond(1783077681491L));
        order.setRemark("test");

        assertThatThrownBy(() -> orderService.insertOrderBatch(List.of(order)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderMapper, never()).insertBatch(any());
    }

    @Test
    void returnOrder_setsReturnedFlag() {
        when(securityContext.currentGroupId()).thenReturn(1);
        when(orderMapper.findMeIdById(1, 1)).thenReturn(99);

        orderService.returnOrder(1);

        verify(orderMapper).markReturned(eq(1), anyInt());
    }

    @Test
    void returnOrder_marksMerchandiseUnsold() {
        when(securityContext.currentGroupId()).thenReturn(1);
        when(orderMapper.findMeIdById(1, 1)).thenReturn(99);

        orderService.returnOrder(1);

        verify(merchandiseService).markSold(99, false);
    }

    @Test
    void returnOrder_rejectsUnknownOrder() {
        when(securityContext.currentGroupId()).thenReturn(1);
        when(orderMapper.findMeIdById(1, 1)).thenReturn(null);

        assertThatThrownBy(() -> orderService.returnOrder(1))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderMapper, never()).markReturned(anyInt(), anyInt());
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
