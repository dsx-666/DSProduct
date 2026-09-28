package com.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
// springmvc会对那种get请求传递的参数进行转化，会将string转化成localDate
@Configuration
public class DateConverterConfig {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Converter<String, LocalDate> localDateConverter() {
        return source -> {
            if (source == null || source.isBlank()) {
                return null;
            }
            return LocalDate.parse(source, DATE_FORMATTER);
        };
    }

    @Bean
    public Converter<String, LocalDateTime> localDateTimeConverter() {
        return source -> {
            if (source == null || source.isBlank()) {
                return null;
            }
            return LocalDateTime.parse(source, DATE_TIME_FORMATTER);
        };
    }
}