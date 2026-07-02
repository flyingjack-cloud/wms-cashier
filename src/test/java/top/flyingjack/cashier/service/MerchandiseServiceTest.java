package top.flyingjack.cashier.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.flyingjack.cashier.entity.Category;
import top.flyingjack.cashier.entity.Merchandise;
import top.flyingjack.cashier.entity.MerchandiseWithCategoryDto;
import top.flyingjack.cashier.mapper.MerchandiseMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchandiseServiceTest {

    @Mock MerchandiseMapper merchandiseMapper;
    @Mock WmsSecurityContext securityContext;
    @InjectMocks MerchandiseService merchandiseService;

    @Test
    void insertMerchandise_insertsOnePerImei() {
        when(securityContext.currentGroupId()).thenReturn(1);

        merchandiseService.insertMerchandise(2, new BigDecimal("100"), new BigDecimal("150"),
                List.of("IMEI001", "IMEI002"), Instant.now());

        verify(merchandiseMapper, times(2)).insert(any(Merchandise.class));
    }

    @Test
    void getMerchandiseCount_delegatesToMapper() {
        when(securityContext.currentGroupId()).thenReturn(1);
        when(merchandiseMapper.countByGroupAndSold(1, false)).thenReturn(5);

        int count = merchandiseService.getMerchandiseCount(false);

        assertThat(count).isEqualTo(5);
    }

    @Test
    void getMerchandiseByPage_includesNestedCategory() {
        Category category = new Category();
        category.setName("手机");
        MerchandiseWithCategoryDto merchandise = new MerchandiseWithCategoryDto();
        merchandise.setCategory(category);
        when(securityContext.currentGroupId()).thenReturn(1);
        when(merchandiseMapper.findByGroupPaged(1, false, 20, 0)).thenReturn(List.of(merchandise));

        List<MerchandiseWithCategoryDto> result = merchandiseService.getMerchandiseByPage(20, 0, false);

        assertThat(result.get(0).getCategory().getName()).isEqualTo("手机");
    }
}
