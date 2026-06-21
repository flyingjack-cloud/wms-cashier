package top.flyingjack.cashier.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import top.flyingjack.cashier.BaseContainerTest;
import top.flyingjack.cashier.entity.Category;
import top.flyingjack.cashier.entity.WmsUserProfile;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.mapper.CategoryMapper;
import top.flyingjack.cashier.mapper.WmsUserProfileMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WmsCashierIntegrationTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;

    @Autowired WmsUserProfileMapper profileMapper;
    @Autowired CategoryMapper categoryMapper;

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
}
