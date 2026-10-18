package com.product.pojo.dto.response;

import com.product.pojo.bo.AdminUserListItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminUserListResponse {
    private Long total;
    private List<AdminUserListItem> userList;
}
