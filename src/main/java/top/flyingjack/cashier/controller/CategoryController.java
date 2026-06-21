package top.flyingjack.cashier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.flyingjack.cashier.entity.Category;
import top.flyingjack.cashier.service.CategoryService;
import top.flyingjack.common.dto.ApiRes;

import java.util.List;

@RestController
@RequestMapping("/category")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<ApiRes<List<Category>>> getCategoriesByParentId(@PathVariable int parentId) {
        return ResponseEntity.ok(ApiRes.success(categoryService.getCategoriesByParentId(parentId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiRes<Category>> getCategoryById(@PathVariable int id) {
        return ResponseEntity.ok(ApiRes.success(categoryService.getCategory(id)));
    }

    @PostMapping("/")
    public ResponseEntity<ApiRes<Integer>> insertCategory(@RequestParam int parentId, @RequestParam String name) {
        return ResponseEntity.ok(ApiRes.success(categoryService.insertCategory(parentId, name)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiRes<Void>> deleteCategoryById(@PathVariable int id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiRes.success());
    }
}
