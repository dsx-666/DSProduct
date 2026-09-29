package com.product.pojo.other;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Scope {
    private String mode;
    private List<String> allowedBrands;
    private Boolean canViewFullOrder;
    private Boolean canManageUsers;
}
