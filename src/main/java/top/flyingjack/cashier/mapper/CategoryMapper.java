package top.flyingjack.cashier.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.flyingjack.cashier.entity.Category;

import java.util.List;

@Mapper
public interface CategoryMapper {
    List<Category> findByParentId(@Param("groupId") int groupId, @Param("parentId") int parentId);
    Category findById(@Param("id") int id);
    void insert(Category category);
    void deleteById(@Param("id") int id);
}
