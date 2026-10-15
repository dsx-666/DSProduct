package com.product.pojo.bo;

import com.product.pojo.dto.request.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
//TODO:后续可以添加权限list，并且需要修改springSecurity权限相关的内容
public class Scope {
    private String mode;
    private List<String> allowedBrands;
    private Boolean canViewFullOrder;
    private Boolean canManageUsers;

    public Boolean validateFilter(Filter filter) {
        Set<String> allowedBrandSet = new HashSet<>(this.allowedBrands);

        List<String> brands = filter.getBrands();
        if(this.mode.equals("ALL")){
            return true;
        }

        if (brands.isEmpty()) {
            return true;
        }

        if (this.allowedBrands.isEmpty()) {
            return false;
        }

        return allowedBrandSet.containsAll(brands);
    }

}
