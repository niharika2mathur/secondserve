package com.secondserve.config;

import com.secondserve.service.AppContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    /** Single wiring point for repositories and services (framework-free classes, so they are unit-testable). */
    @Bean
    public AppContext appContext(@Value("${secondserve.bloom.expected-insertions:1000}") int expected,
                                 @Value("${secondserve.bloom.false-positive-rate:0.01}") double fpp,
                                 @Value("${secondserve.load-sample-data:true}") boolean loadSample) {
        return new AppContext(expected, fpp, loadSample);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
