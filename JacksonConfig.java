package com.example.filters.config;

import com.example.filters.json.FilterDeserializer;
import com.example.filters.model.Filter;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Module filterDeserializerModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Filter.class, new FilterDeserializer());
        return module;
    }
}
