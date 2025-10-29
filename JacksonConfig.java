package com.example.filterdemo.config;

import com.example.filterdemo.json.FilterDeserializer;
import com.example.filterdemo.model.CompositeFilter;
import com.example.filterdemo.model.Filter;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Module filterDeserializerModule() {
        SimpleModule module = new SimpleModule();
        FilterDeserializer deserializer = new FilterDeserializer();
        module.addDeserializer(Filter.class, deserializer);
        module.addDeserializer(CompositeFilter.class, deserializer);
        return module;
    }
}
