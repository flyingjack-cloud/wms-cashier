package top.flyingjack.cashier.service;

import org.springframework.stereotype.Service;
import top.flyingjack.cashier.entity.Category;
import top.flyingjack.cashier.mapper.CategoryMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryMapper categoryMapper;
    private final WmsSecurityContext securityContext;

    public CategoryService(CategoryMapper categoryMapper, WmsSecurityContext securityContext) {
        this.categoryMapper = categoryMapper;
        this.securityContext = securityContext;
    }

    public List<Category> getCategoriesByParentId(int parentId) {
        return categoryMapper.findByParentId(securityContext.currentGroupId(), parentId);
    }

    public Category getCategory(int id) {
        return categoryMapper.findById(id, securityContext.currentGroupId());
    }

    public int insertCategory(int parentId, String name) {
        Category category = new Category();
        category.setGroupId(securityContext.currentGroupId());
        category.setParentId(parentId);
        category.setName(name);
        categoryMapper.insert(category);
        return category.getId();
    }

    public void deleteCategory(int id) {
        categoryMapper.deleteById(id, securityContext.currentGroupId());
    }
}
