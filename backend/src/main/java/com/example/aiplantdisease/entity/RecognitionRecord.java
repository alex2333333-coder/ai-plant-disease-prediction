package com.example.aiplantdisease.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "recognition_record")
public class RecognitionRecord {

  @Id
  private String id;

  @Column(name = "crop_type", nullable = false)
  private String cropType;

  @Column(name = "disease_name", nullable = false)
  private String diseaseName;

  @Column(name = "hazard_level", nullable = false)
  private String hazardLevel;

  @Column(name = "prevention_advice", length = 4000)
  private String preventionAdvice;

  @Column(name = "image_key", nullable = false, length = 500)
  private String imageKey;

  @Column(name = "is_alert", nullable = false)
  private boolean isAlert;

  @Column(name = "create_time", nullable = false)
  private Instant createTime;

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

  public String getImageKey() {
    return imageKey;
  }

  public void setImageKey(String imageKey) {
    this.imageKey = imageKey;
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
}

