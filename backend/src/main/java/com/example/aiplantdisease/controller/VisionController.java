package com.example.aiplantdisease.controller;

import com.example.aiplantdisease.api.ApiResponse;
import com.example.aiplantdisease.dto.RecognizeRequest;
import com.example.aiplantdisease.dto.RecognizeResponse;
import com.example.aiplantdisease.dto.RecognitionRecordDto;
import com.example.aiplantdisease.dto.RecognitionRecordListResponse;
import com.example.aiplantdisease.dto.SystemConfigResponse;
import com.example.aiplantdisease.service.SystemConfigService;
import com.example.aiplantdisease.service.VisionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class VisionController {

  private final VisionService visionService;
  private final SystemConfigService systemConfigService;

  public VisionController(VisionService visionService, SystemConfigService systemConfigService) {
    this.visionService = visionService;
    this.systemConfigService = systemConfigService;
  }

  @PostMapping("/recognize")
  public ApiResponse<RecognizeResponse> recognize(@Valid @RequestBody RecognizeRequest request) throws Exception {
    return ApiResponse.ok(visionService.recognize(request));
  }

  @GetMapping("/config")
  public ApiResponse<SystemConfigResponse> config() {
    return ApiResponse.ok(systemConfigService.getSystemConfig());
  }

  @GetMapping("/records")
  public ApiResponse<RecognitionRecordListResponse> records(
    @RequestParam(value = "limit", required = false, defaultValue = "20") int limit
  ) {
    RecognitionRecordListResponse resp = new RecognitionRecordListResponse();
    resp.setItems(visionService.listLatest(limit));
    return ApiResponse.ok(resp);
  }
}

