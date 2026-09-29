package com.product.pojo.vo;


import com.fasterxml.jackson.annotation.JsonProperty;
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
public class FilterVo {
    LocalDate startTime;
    LocalDate endTime;
    @JsonProperty("category")
    List<String> categories;
    List<String> brand;
    List<String> statuses;
}
