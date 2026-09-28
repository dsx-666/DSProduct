package com.product.controller;

import com.product.pojo.vo.Result;
import com.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductService productService;
    @GetMapping("/categories")
    public Result<List<String>> getCategories() {
        // productService.getCategories()返回的是一个List<String>
        return Result.success(productService
                .getCategories());
    }


}
