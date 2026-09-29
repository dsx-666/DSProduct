package com.product.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class OrderListRequest extends FilterRequest{

    private List<String> statuses;
    private String keyWord;
    @NotBlank(message = "必须要有对应页码才可以查询具体第几页")
    private Long page;
    @NotBlank(message = "必须要有每页条数才可以进行分页查询")
    private Long num;
}
