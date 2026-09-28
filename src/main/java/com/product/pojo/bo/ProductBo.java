package com.product.pojo.bo;

import com.product.pojo.po.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBo {
    private Set<String> categories;

    public boolean containsCategory(List<String> categories) {
        // 判断 listA 是否包含 listB 的所有元素
        return this.categories.containsAll(categories);
    }
}
