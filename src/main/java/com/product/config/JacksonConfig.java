package com.product.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
    在spring进行序列化和反序列化的时候会识别转化的类型，
    在反序列化的时候将string转localDate而序列化就是localDate转string
*/
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {

        return builder -> builder
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                )
                .serializerByType(
                        LocalDate.class,
                        new LocalDateSerializer(DATE_FORMATTER)
                )
                .deserializerByType(
                        LocalDate.class,
                        new LocalDateDeserializer(DATE_FORMATTER)
                )
                .serializerByType(
                        LocalDateTime.class,
                        new LocalDateTimeSerializer(DATE_TIME_FORMATTER)
                )
                .deserializerByType(
                        LocalDateTime.class,
                        new LocalDateTimeDeserializer(DATE_TIME_FORMATTER)
                );
    }
}