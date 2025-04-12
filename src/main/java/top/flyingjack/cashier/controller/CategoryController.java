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
        return Arrays.asList(
                new Category(1 ,0 , "OPPO", 1, 1),
                new Category(2 ,0 , "XIAOMI", 1, 1)
                );
    }
}
