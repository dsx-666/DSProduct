package com.product.pojo.vo;

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
public class AdminUserListVo {
    private Long total;
    private Long pageNum;
    private Long pageSize;
    private List<AdminUserListItem> userList;
}
