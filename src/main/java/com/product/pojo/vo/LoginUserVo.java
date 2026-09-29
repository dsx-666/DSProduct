package com.product.pojo.vo;


import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.product.enums.Role;
import com.product.pojo.other.Scope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginUserVo {

    private User user;

    private String JwtToken;

    private Scope scope;
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class User{
        private String email;
        private String userName;
        private LocalDate CreateTime;
        @JsonUnwrapped
        private Role role;
        private Long UserId;
        private String brand;
    }

}
