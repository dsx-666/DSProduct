package com.product.service;
import com.product.pojo.bo.ProductBo;

import java.util.List;

public interface ProductService {
    List<String> getCategories();
    List<Object> selectCategories();
}
