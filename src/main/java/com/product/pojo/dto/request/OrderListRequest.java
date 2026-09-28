package com.product.pojo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderListRequest {
    private LocalDate startTime;
    private LocalDate endTime;
    private List<String> statuses;
    private String keyWord;

    @NotBlank(message = "必须要有对应页码才可以查询具体第几页")
    private Long page;
    @NotBlank(message = "必须要有每页条数才可以进行分页查询")
    private Long num;
}
