package com.example.aiplantdisease.dto;

public class SystemConfigResponse {
  private String cropTypeDefault;
  // 前端本地预处理阈值（仅用于“是否触发抓拍”，不参与AI判断准确性）
  private double leafScoreThreshold;
  private double abnormalRatioThreshold;
  private int debounceFrames;
  private int lockoutSeconds;

  public String getCropTypeDefault() {
    return cropTypeDefault;
  }

  public void setCropTypeDefault(String cropTypeDefault) {
    this.cropTypeDefault = cropTypeDefault;
  }

  public double getLeafScoreThreshold() {
    return leafScoreThreshold;
  }

  public void setLeafScoreThreshold(double leafScoreThreshold) {
    this.leafScoreThreshold = leafScoreThreshold;
  }

  public double getAbnormalRatioThreshold() {
    return abnormalRatioThreshold;
  }

  public void setAbnormalRatioThreshold(double abnormalRatioThreshold) {
    this.abnormalRatioThreshold = abnormalRatioThreshold;
  }

  public int getDebounceFrames() {
    return debounceFrames;
  }

  public void setDebounceFrames(int debounceFrames) {
    this.debounceFrames = debounceFrames;
  }

  public int getLockoutSeconds() {
    return lockoutSeconds;
  }

  public void setLockoutSeconds(int lockoutSeconds) {
    this.lockoutSeconds = lockoutSeconds;
  }
}

