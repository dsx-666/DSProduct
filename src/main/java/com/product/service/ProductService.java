package com.product.service;
import com.product.enums.ProductCategory;
import com.product.pojo.bo.ProductBo;

import java.util.List;

public interface ProductService {
    List<ProductCategory> getCategories();
    List<Object> selectCategories();
}
