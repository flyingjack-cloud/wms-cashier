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

    @Test
    void searchMerchandise_includesNestedCategory() {
        Category category = new Category();
        category.setName("平板");
        MerchandiseWithCategoryDto merchandise = new MerchandiseWithCategoryDto();
        merchandise.setCategory(category);
        when(securityContext.currentGroupId()).thenReturn(1);
        when(merchandiseMapper.searchByGroupAndText(1, "IMEI", false)).thenReturn(List.of(merchandise));

        List<MerchandiseWithCategoryDto> result = merchandiseService.searchMerchandise("IMEI", false);

        assertThat(result.get(0).getCategory().getName()).isEqualTo("平板");
    }

    @Test
    void getMerchandiseByCateId_filtersBySold() {
        Merchandise merchandise = new Merchandise();
        merchandise.setId(3);
        when(securityContext.currentGroupId()).thenReturn(1);
        when(merchandiseMapper.findByCateId(1, 2, false)).thenReturn(List.of(merchandise));

        List<Merchandise> result = merchandiseService.getMerchandiseByCateId(2, false);

        assertThat(result).containsExactly(merchandise);
    }

    @Test
    void findById_delegatesToMapper() {
        Merchandise merchandise = new Merchandise();
        merchandise.setId(7);
        when(securityContext.currentGroupId()).thenReturn(1);
        when(merchandiseMapper.findById(7, 1)).thenReturn(merchandise);

        Merchandise result = merchandiseService.findById(7);

        assertThat(result).isSameAs(merchandise);
    }

    @Test
    void markSold_delegatesToMapper() {
        when(securityContext.currentGroupId()).thenReturn(1);

        merchandiseService.markSold(7, true);

        verify(merchandiseMapper).updateSoldStatus(7, 1, true);
    }
}
