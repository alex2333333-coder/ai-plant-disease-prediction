package com.example.aiplantdisease.dto;

import java.util.List;

public class RecognitionRecordListResponse {
  private List<RecognitionRecordDto> items;

  public List<RecognitionRecordDto> getItems() {
    return items;
  }

  public void setItems(List<RecognitionRecordDto> items) {
    this.items = items;
  }
}

