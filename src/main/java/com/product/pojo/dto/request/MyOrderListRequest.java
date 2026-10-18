package com.product.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyOrderListRequest {
    private Long page;
    private Long num;
    private LocalDate startTime;
    private LocalDate endTime;
    private String keyWord;
    private List<String> statuses;
    private Long offset;
}
