package top.flyingjack.cashier.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.flyingjack.cashier.entity.Category;

import java.util.Arrays;
import java.util.List;

@RestController
public class CategoryController {
    @GetMapping("/test")
    public List<Category> test(){
        Category c1 = new Category();
        c1.setId(1);
        c1.setParentId(0);
        c1.setName("OPPO");
        c1.setGroupId(1);

        Category c2 = new Category();
        c2.setId(2);
        c2.setParentId(0);
        c2.setName("XIAOMI");
        c2.setGroupId(1);

        return Arrays.asList(c1, c2);
    }
}
