package com.example.aiplantdisease.api;

import java.time.Instant;

public class ApiResponse<T> {
  private int code;
  private String message;
  private T data;
  private long timestamp;

  public static <T> ApiResponse<T> ok(T data) {
    ApiResponse<T> r = new ApiResponse<>();
    r.code = 0;
    r.message = "success";
    r.data = data;
    r.timestamp = Instant.now().toEpochMilli();
    return r;
  }

  public static <T> ApiResponse<T> fail(int code, String message) {
    ApiResponse<T> r = new ApiResponse<>();
    r.code = code;
    r.message = message;
    r.data = null;
    r.timestamp = Instant.now().toEpochMilli();
    return r;
  }

  public int getCode() {
    return code;
  }

  public void setCode(int code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }
}

