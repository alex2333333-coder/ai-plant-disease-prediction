package com.example.aiplantdisease.dto;

import java.time.Instant;

public class RecognitionRecordDto {
  private String id;
  private String cropType;
  private String diseaseName;
  private String hazardLevel;
  private String preventionAdvice;
  private boolean isAlert;
  private Instant createTime;
  private String imageUrl; // 临时签名URL

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCropType() {
    return cropType;
  }

  public void setCropType(String cropType) {
    this.cropType = cropType;
  }

  public String getDiseaseName() {
    return diseaseName;
  }

  public void setDiseaseName(String diseaseName) {
    this.diseaseName = diseaseName;
  }

  public String getHazardLevel() {
    return hazardLevel;
  }

  public void setHazardLevel(String hazardLevel) {
    this.hazardLevel = hazardLevel;
  }

  public String getPreventionAdvice() {
    return preventionAdvice;
  }

  public void setPreventionAdvice(String preventionAdvice) {
    this.preventionAdvice = preventionAdvice;
  }

  public boolean isAlert() {
    return isAlert;
  }

  public void setAlert(boolean alert) {
    isAlert = alert;
  }

  public Instant getCreateTime() {
    return createTime;
  }

  public void setCreateTime(Instant createTime) {
    this.createTime = createTime;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }
}

