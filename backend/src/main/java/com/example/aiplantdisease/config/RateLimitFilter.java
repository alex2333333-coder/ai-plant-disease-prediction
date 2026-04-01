package com.example.aiplantdisease.config;

import com.example.aiplantdisease.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

  @Value("${app.rateLimit.recognizePerMinute:30}")
  private long recognizePerMinute;

  private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // 仅对识别接口限流
    return !("/api/v1/recognize".equals(path));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
    throws ServletException, IOException {
    String key = request.getRemoteAddr();
    long now = Instant.now().toEpochMilli();
    long windowMs = 60_000L;

    WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());
    synchronized (counter) {
      if (now - counter.windowStartMs >= windowMs) {
        counter.windowStartMs = now;
        counter.count.set(0);
      }

      long next = counter.count.incrementAndGet();
      if (next > recognizePerMinute) {
        // 直接拒绝，避免API费用超额。
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
          toJson(ApiResponse.fail(429, "请求过于频繁，请稍后再试"))
        );
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private String toJson(ApiResponse<?> r) {
    // 简化：避免引入额外 JSON 库。生产建议使用 ObjectMapper。
    return "{\"code\":" + r.getCode() + ",\"message\":\"" + escape(r.getMessage()) + "\",\"data\":null,\"timestamp\":" + r.getTimestamp() + "}";
  }

  private String escape(String s) {
    if (s == null) return "";
    return s.replace("\"", "\\\"");
  }

  private static class WindowCounter {
    private long windowStartMs = Instant.now().toEpochMilli();
    private final AtomicLong count = new AtomicLong(0);
  }
}

