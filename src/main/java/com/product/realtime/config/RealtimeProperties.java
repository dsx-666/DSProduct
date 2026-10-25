package com.product.realtime.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "realtime")
public class RealtimeProperties {
    private Duration ticketTtl = Duration.ofSeconds(30);
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://127.0.0.1:4173",
            "http://localhost:4173"
    ));
}
