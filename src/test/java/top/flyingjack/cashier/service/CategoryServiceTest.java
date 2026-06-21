package top.flyingjack.cashier.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.flyingjack.cashier.entity.Category;
import top.flyingjack.cashier.mapper.CategoryMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryMapper categoryMapper;
    @Mock WmsSecurityContext securityContext;
    @InjectMocks CategoryService categoryService;

    @Test
    void getCategoriesByParentId_returnsGroupScoped() {
        when(securityContext.currentGroupId()).thenReturn(1);
        Category cat = new Category();
        cat.setId(10); cat.setName("OPPO"); cat.setParentId(0); cat.setGroupId(1);
        when(categoryMapper.findByParentId(1, 0)).thenReturn(List.of(cat));

        List<Category> result = categoryService.getCategoriesByParentId(0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("OPPO");
        verify(categoryMapper).findByParentId(1, 0);
    }

    @Test
    void insertCategory_returnsNewId() {
        when(securityContext.currentGroupId()).thenReturn(1);
        doAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(5);
            return null;
        }).when(categoryMapper).insert(any());

        int id = categoryService.insertCategory(0, "手机");

        assertThat(id).isEqualTo(5);
    }
}
