package com.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.product.mapper.ProductMapper;
import com.product.pojo.bo.ProductBo;
import com.product.pojo.po.Product;
import com.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ProductServiceImpl implements ProductService {
    private final ProductMapper productMapper;
    @Override
    public List<Object> selectCategories() {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Product::getCategory)
                .groupBy(Product::getCategory);
        return productMapper.selectObjs(wrapper);
    }
    @Override
    public List<String> getCategories() {
        return selectCategories().stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toList();
    }
}
