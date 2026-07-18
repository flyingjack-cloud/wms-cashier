package top.flyingjack.cashier.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import top.flyingjack.cashier.BaseContainerTest;
import top.flyingjack.cashier.entity.Order;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.mapper.OrderMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderIntegrationTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;
    @MockBean JwtDecoder jwtDecoder;

    @Autowired OrderMapper orderMapper;

    private Order order(int groupId, int meId) {
        Order o = new Order();
        o.setGroupId(groupId);
        o.setMeId(meId);
        o.setSellingPrice(new BigDecimal("999.00"));
        o.setSellingTime(Instant.now());
        o.setRemark("test");
        o.setReturned(false);
        return o;
    }

    @Test
    void insertBatch_assignsDistinctGeneratedIdsToEachOrderInListOrder() {
        Order first = order(950, 10);
        Order second = order(950, 11);
        Order third = order(950, 12);
        List<Order> orders = List.of(first, second, third);

        orderMapper.insertBatch(orders);

        assertThat(first.getId()).isGreaterThan(0);
        assertThat(second.getId()).isGreaterThan(first.getId());
        assertThat(third.getId()).isGreaterThan(second.getId());
    }
}
