package com.product.pojo.dto.request;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.product.MyException.BusinessException;
import com.product.converter.UserConverter;
import com.product.enums.Code;
import com.product.pojo.vo.Scope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.BindParam;

import java.lang.management.RuntimeMXBean;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    @JsonProperty("brand")
    /*
    * 类似于JsonProperty
    * */
    @BindParam("brand")
    List<String> brands;
    List<String> statuses;
}
