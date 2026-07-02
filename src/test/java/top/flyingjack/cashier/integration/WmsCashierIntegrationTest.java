package top.flyingjack.cashier.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import top.flyingjack.cashier.BaseContainerTest;
import top.flyingjack.cashier.entity.Category;
import top.flyingjack.cashier.entity.Merchandise;
import top.flyingjack.cashier.entity.MerchandiseWithCategoryDto;
import top.flyingjack.cashier.entity.Order;
import top.flyingjack.cashier.entity.OrderListItemDto;
import top.flyingjack.cashier.entity.WmsUserProfile;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.mapper.CategoryMapper;
import top.flyingjack.cashier.mapper.MerchandiseMapper;
import top.flyingjack.cashier.mapper.OrderMapper;
import top.flyingjack.cashier.mapper.WmsUserProfileMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WmsCashierIntegrationTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;
    @MockBean JwtDecoder jwtDecoder;

    @Autowired WmsUserProfileMapper profileMapper;
    @Autowired CategoryMapper categoryMapper;
    @Autowired MerchandiseMapper merchandiseMapper;
    @Autowired OrderMapper orderMapper;

    @Test
    void profileLazyInit_insertsDefaultRow() {
        WmsUserProfile profile = new WmsUserProfile(999L, 0, "ROLE_DEFAULT");
        profileMapper.insert(profile);

        WmsUserProfile found = profileMapper.findByUserId(999L);
        assertThat(found).isNotNull();
        assertThat(found.getGroupId()).isEqualTo(0);
        assertThat(found.getRole()).isEqualTo("ROLE_DEFAULT");
    }

    @Test
    void category_insertAndQuery() {
        // wms_category has no FK to wms_group, so insert directly
        Category cat = new Category();
        cat.setGroupId(1);
        cat.setParentId(0);
        cat.setName("手机");
        categoryMapper.insert(cat);
        assertThat(cat.getId()).isGreaterThan(0);

        List<Category> list = categoryMapper.findByParentId(1, 0);
        assertThat(list).anyMatch(c -> "手机".equals(c.getName()));
    }

    @Test
    void findByGroupAndDateRangePaged_nestsMerchandiseWhenPresent() {
        Merchandise merchandise = new Merchandise();
        merchandise.setGroupId(777);
        merchandise.setCateId(1);
        merchandise.setCost(new BigDecimal("100.00"));
        merchandise.setPrice(new BigDecimal("150.00"));
        merchandise.setImei("IMEI-NEST-001");
        merchandise.setSold(true);
        merchandise.setCreatedAt(Instant.now());
        merchandiseMapper.insert(merchandise);

        Order order = new Order();
        order.setGroupId(777);
        order.setMeId(merchandise.getId());
        order.setSellingPrice(new BigDecimal("150.00"));
        order.setSellingTime(Instant.now());
        order.setRemark("测试");
        order.setReturned(false);
        orderMapper.insert(order);

        List<OrderListItemDto> orders = orderMapper.findByGroupAndDateRangePaged(
                777, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 20, 0);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getMerchandise()).isNotNull();
        assertThat(orders.get(0).getMerchandise().getCost()).isEqualByComparingTo("100.00");
        assertThat(orders.get(0).getMerchandise().getImei()).isEqualTo("IMEI-NEST-001");
    }

    @Test
    void findByGroupAndDateRangePaged_nullMerchandiseWhenDeleted() {
        Merchandise merchandise = new Merchandise();
        merchandise.setGroupId(778);
        merchandise.setCateId(1);
        merchandise.setCost(new BigDecimal("100.00"));
        merchandise.setPrice(new BigDecimal("150.00"));
        merchandise.setImei("IMEI-NEST-002");
        merchandise.setSold(true);
        merchandise.setCreatedAt(Instant.now());
        merchandiseMapper.insert(merchandise);

        Order order = new Order();
        order.setGroupId(778);
        order.setMeId(merchandise.getId());
        order.setSellingPrice(new BigDecimal("150.00"));
        order.setSellingTime(Instant.now());
        order.setRemark("测试");
        order.setReturned(false);
        orderMapper.insert(order);

        merchandiseMapper.deleteById(merchandise.getId(), 778);

        List<OrderListItemDto> orders = orderMapper.findByGroupAndDateRangePaged(
                778, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 20, 0);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getMerchandise()).isNull();
    }

    @Test
    void findByGroupAndDateRangePaged_nestsCategoryWithinMerchandise() {
        Category category = new Category();
        category.setGroupId(779);
        category.setParentId(0);
        category.setName("手机");
        categoryMapper.insert(category);

        Merchandise merchandise = new Merchandise();
        merchandise.setGroupId(779);
        merchandise.setCateId(category.getId());
        merchandise.setCost(new BigDecimal("100.00"));
        merchandise.setPrice(new BigDecimal("150.00"));
        merchandise.setImei("IMEI-NEST-003");
        merchandise.setSold(true);
        merchandise.setCreatedAt(Instant.now());
        merchandiseMapper.insert(merchandise);

        Order order = new Order();
        order.setGroupId(779);
        order.setMeId(merchandise.getId());
        order.setSellingPrice(new BigDecimal("150.00"));
        order.setSellingTime(Instant.now());
        order.setRemark("测试");
        order.setReturned(false);
        orderMapper.insert(order);

        List<OrderListItemDto> orders = orderMapper.findByGroupAndDateRangePaged(
                779, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 20, 0);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getMerchandise().getCategory()).isNotNull();
        assertThat(orders.get(0).getMerchandise().getCategory().getName()).isEqualTo("手机");
    }

    @Test
    void findByGroupAndDateRangePaged_nullCategoryWhenDeleted() {
        Category category = new Category();
        category.setGroupId(780);
        category.setParentId(0);
        category.setName("电脑");
        categoryMapper.insert(category);

        Merchandise merchandise = new Merchandise();
        merchandise.setGroupId(780);
        merchandise.setCateId(category.getId());
        merchandise.setCost(new BigDecimal("100.00"));
        merchandise.setPrice(new BigDecimal("150.00"));
        merchandise.setImei("IMEI-NEST-004");
        merchandise.setSold(true);
        merchandise.setCreatedAt(Instant.now());
        merchandiseMapper.insert(merchandise);

        Order order = new Order();
        order.setGroupId(780);
        order.setMeId(merchandise.getId());
        order.setSellingPrice(new BigDecimal("150.00"));
        order.setSellingTime(Instant.now());
        order.setRemark("测试");
        order.setReturned(false);
        orderMapper.insert(order);

        categoryMapper.deleteById(category.getId(), 780);

        List<OrderListItemDto> orders = orderMapper.findByGroupAndDateRangePaged(
                780, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 20, 0);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getMerchandise()).isNotNull();
        assertThat(orders.get(0).getMerchandise().getCategory()).isNull();
    }

    @Test
    void findByGroupPaged_nestsCategory() {
        Category category = new Category();
        category.setGroupId(781);
        category.setParentId(0);
        category.setName("平板");
        categoryMapper.insert(category);

        Merchandise merchandise = new Merchandise();
        merchandise.setGroupId(781);
        merchandise.setCateId(category.getId());
        merchandise.setCost(new BigDecimal("100.00"));
        merchandise.setPrice(new BigDecimal("150.00"));
        merchandise.setImei("IMEI-NEST-005");
        merchandise.setSold(false);
        merchandise.setCreatedAt(Instant.now());
        merchandiseMapper.insert(merchandise);

        List<MerchandiseWithCategoryDto> result = merchandiseMapper.findByGroupPaged(781, false, 20, 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isNotNull();
        assertThat(result.get(0).getCategory().getName()).isEqualTo("平板");
    }

    @Test
    void findByGroupPaged_nullCategoryWhenDeleted() {
        Category category = new Category();
        category.setGroupId(782);
        category.setParentId(0);
        category.setName("耳机");
        categoryMapper.insert(category);

        Merchandise merchandise = new Merchandise();
        merchandise.setGroupId(782);
        merchandise.setCateId(category.getId());
        merchandise.setCost(new BigDecimal("100.00"));
        merchandise.setPrice(new BigDecimal("150.00"));
        merchandise.setImei("IMEI-NEST-006");
        merchandise.setSold(false);
        merchandise.setCreatedAt(Instant.now());
        merchandiseMapper.insert(merchandise);

        categoryMapper.deleteById(category.getId(), 782);

        List<MerchandiseWithCategoryDto> result = merchandiseMapper.findByGroupPaged(782, false, 20, 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isNull();
    }
}
