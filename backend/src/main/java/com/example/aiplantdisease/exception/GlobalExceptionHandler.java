package com.example.aiplantdisease.exception;

import com.example.aiplantdisease.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(AppException.class)
  public ApiResponse<?> onAppException(AppException e) {
    log.warn("AppException: code={}, message={}", e.getCode(), e.getMessage());
    return ApiResponse.fail(e.getCode(), e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ApiResponse<?> onValidation(MethodArgumentNotValidException e) {
    Map<String, String> errors = new HashMap<>();
    e.getBindingResult().getFieldErrors()
      .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
    log.warn("Validation error: {}", errors);
    return ApiResponse.fail(400, "参数校验失败");
  }

  @ExceptionHandler(Exception.class)
  public ApiResponse<?> onOther(Exception e) {
    log.error("Unhandled exception", e);
    return ApiResponse.fail(500, "服务器内部错误");
  }
}

