package com.product.mapper;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BrandBusinessMapperAnnotationTest {
    @Test
    void shouldParseBrandBusinessMapperAnnotations() {
        assertDoesNotThrow(() -> {
            Configuration configuration = new Configuration();
            new MapperAnnotationBuilder(configuration, BrandBusinessMapper.class).parse();
        });
    }
}
