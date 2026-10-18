package com.product.pojo.dto.response;

import com.product.pojo.bo.MyOrderListItem;
import com.product.pojo.bo.Scope;
import com.product.pojo.dto.request.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyOrderListResponse {
    private Filter filters;
    private Scope scope;
    private String keyWord;
    private Long total;
    private Long pageNum;
    private Long pageSize;
    private List<MyOrderListItem> orderList;
}
