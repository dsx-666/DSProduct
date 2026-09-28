package com.product.pojo.dto.request;

import java.time.LocalDate;
import java.util.List;
public class KpiAnalysisRequest {
    LocalDate startTime;
    LocalDate endTime;
    List<String> categories;
}
