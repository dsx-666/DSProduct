package com.product.pojo.other;


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
public class Filter {
    LocalDate startTime;
    LocalDate endTime;
    @JsonProperty("category")
    List<String> categories;
    @JsonProperty("brand")
    List<String> brands;
    List<String> statuses;
}
