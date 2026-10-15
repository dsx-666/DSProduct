package com.product.pojo.dto.request;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.BindParam;

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
    @BindParam("category")
    List<String> categories;
    /*
    * 类似于JsonProperty
    * */
    @JsonProperty("brand")
    @BindParam("brand")
    List<String> brands;
    List<String> statuses;
    /*
    * springmvc底层是通过set方法来注入这种传参数据的
    *
    * */
    public void setBrand(List<String> brands) {
        this.brands = brands;
    }

    public void setCategory(List<String> categories) {
        this.categories = categories;
    }
}
