package com.product.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminUserListRequest {
    private Long page;
    private Long num;
    private String keyWord;
    private String role;
    private String status;
    private Long offset;
}
