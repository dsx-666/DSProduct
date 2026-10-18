package com.product.mapper;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MapperAnnotationParsingTest {
    @Test
    void shouldParseOrderAndUserMapperAnnotations() {
        assertDoesNotThrow(() -> {
            Configuration configuration = new Configuration();
            new MapperAnnotationBuilder(configuration,OrderMapper.class).parse();
            new MapperAnnotationBuilder(configuration,UserMapper.class).parse();
        });
    }
}
