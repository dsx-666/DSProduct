package com.product.pojo.dto.request;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderListRequest {
    @JsonUnwrapped
    private Filter filters = new Filter();
    private String keyWord;
    @NotBlank(message = "必须要有对应页码才可以查询具体第几页")
    private Long page;
    @NotBlank(message = "必须要有每页条数才可以进行分页查询")
    private Long num;
    private Long offset;
    public void setCategory(List<String> categories) {
        filters.setCategories(categories);
    }

    public void setStatuses(List<String> statuses) {
        filters.setStatuses(statuses);
    }
    public void setStartTime(LocalDate startTime) {
        filters.setStartTime(startTime);
    }
    public void setEndTime(LocalDate endTime) {
        filters.setEndTime(endTime);
    }
    public void setBrand(List<String> brands) {
        filters.setBrands(brands);
    }
}
