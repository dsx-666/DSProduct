package com.product.pojo.dto.request;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class FilterRequest {
    LocalDate startTime;
    LocalDate endTime;
    @JsonProperty("category")
    List<String> categories;
    List<String> brand;
}
