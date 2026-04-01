package com.example.aiplantdisease.dto;

public class RecognizeResponse {
  private String recordId;
  private boolean isAlert;
  private String cropType;
  private String diseaseName;
  private String hazardLevel;
  private String preventionAdvice;
  private String imageUrl;

  public String getRecordId() {
    return recordId;
  }

  public void setRecordId(String recordId) {
    this.recordId = recordId;
  }

  public boolean isAlert() {
    return isAlert;
  }

  public void setAlert(boolean alert) {
    isAlert = alert;
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

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }
}

