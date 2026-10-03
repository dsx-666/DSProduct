package com.product.pojo.dto.response;

import com.product.enums.Role;
import com.product.pojo.vo.Scope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private String email;
    private String userName;
    private LocalDate createTime;
    private Role role;
    private Long userId;
    private String brand;
    private String jwtToken;
    private Scope scope;
}
