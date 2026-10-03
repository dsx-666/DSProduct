package com.product.pojo.vo;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.product.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterUserVo {
    private TempUser user;

    private Scope scope;
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TempUser{
        private String email;
        private String userName;
        private LocalDate CreateTime;
        @JsonUnwrapped
        private Role role;
        private Long UserId;
        private String brand;
    }
}
