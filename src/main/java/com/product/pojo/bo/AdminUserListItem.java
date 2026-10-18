package com.product.pojo.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminUserListItem {
    private Long userId;
    private String email;
    private String name;
    private String role;
    private String roleLabel;
    private String brand;
    private String status;
    private Long version;
    private LocalDate createdAt;
}
