package com.example.aiplantdisease.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @Value("${app.frontendBaseUrl:http://localhost:5173}")
  private String frontendBaseUrl;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    // 只允许前端配置的单一源，避免“全开”带来的安全风险。
    String origin = frontendBaseUrl;
    registry.addMapping("/api/**")
      .allowedOrigins(origin)
      .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
      .allowedHeaders("*")
      .allowCredentials(false)
      .maxAge(3600);
  }
}

