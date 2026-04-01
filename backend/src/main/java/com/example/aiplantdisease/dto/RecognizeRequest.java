package com.example.aiplantdisease.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RecognizeRequest {
  // 前端传 base64 dataURL 或纯 base64
  @NotBlank
  @Size(max = 8_000_000) // 防止超长请求
  private String imageBase64;

  private String cropType;

  public String getImageBase64() {
    return imageBase64;
  }

  public void setImageBase64(String imageBase64) {
    this.imageBase64 = imageBase64;
  }

  public String getCropType() {
    return cropType;
  }

  public void setCropType(String cropType) {
    this.cropType = cropType;
  }
}

