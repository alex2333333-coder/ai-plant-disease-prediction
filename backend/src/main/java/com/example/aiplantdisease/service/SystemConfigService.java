package com.example.aiplantdisease.service;

import com.example.aiplantdisease.dto.SystemConfigResponse;
import org.springframework.stereotype.Service;

@Service
public class SystemConfigService {
  public SystemConfigResponse getSystemConfig() {
    SystemConfigResponse r = new SystemConfigResponse();
    r.setCropTypeDefault("未知作物");
    r.setLeafScoreThreshold(0.4);
    r.setAbnormalRatioThreshold(0.23);
    r.setDebounceFrames(3);
    r.setLockoutSeconds(10);
    return r;
  }
}

