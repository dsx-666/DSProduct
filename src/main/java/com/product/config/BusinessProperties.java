package com.product.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
// 业务时间 后续为了贴近真实情况可以不加的
@Configuration
@ConfigurationProperties(prefix = "business")
@Data
public class BusinessProperties {
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate time;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    // 利用松散规则进行绑定
    private LocalDate startTime;
}
