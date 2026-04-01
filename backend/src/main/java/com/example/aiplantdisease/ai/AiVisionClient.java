package com.example.aiplantdisease.ai;

public interface AiVisionClient {
  String recognize(String prompt, String imageUrl) throws Exception;
}

