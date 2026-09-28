package com.product.pojo.vo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserVo {
    private String userName;
    private String jwtToken;
    private String city;
    private String gender;
    private String email;
}
