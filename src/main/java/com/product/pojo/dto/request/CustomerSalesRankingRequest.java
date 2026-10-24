package com.product.pojo.dto.request;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.BindParam;
import java.time.LocalDate;
import java.util.List;

@Data
public class CustomerSalesRankingRequest {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate startTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate endTime;
    @BindParam("category") private List<String> categories;
    @BindParam("brand") private List<String> brands;
    private List<String> segments;
    private String metric = "amount";
    private Integer limit = 10;
}
