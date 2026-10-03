package com.product.converter;

import com.product.pojo.po.User;
import com.product.pojo.vo.Scope;

import java.util.List;

public class UserConverter {
    public static Scope toScope(User user) {
        Scope scope = new Scope();
        if(user.getRole().getRole().equals("NORMAL_USER")) {
            scope.setMode("OWN");
            scope.setAllowedBrands(List.of());
            scope.setCanViewFullOrder(true);
            scope.setCanManageUsers(false);
        }else if(user.getRole().getRole().equals("ADMIN")) {
            scope.setMode("BRAND");
            scope.setAllowedBrands(List.of(user.getBrand()));
            scope.setCanViewFullOrder(false);
            scope.setCanManageUsers(false);

        }else{
            scope.setMode("ALL");
            scope.setAllowedBrands(List.of());
            scope.setCanViewFullOrder(true);
            scope.setCanManageUsers(true);
        }
        return scope;
    }

}
