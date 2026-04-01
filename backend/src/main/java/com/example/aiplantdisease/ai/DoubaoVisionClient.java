package com.example.aiplantdisease.ai;

import com.example.aiplantdisease.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class DoubaoVisionClient implements AiVisionClient {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Value("${ai.vision.endpoint}")
  private String endpoint;

  @Value("${ai.vision.apiKey}")
  private String apiKey;

  @Value("${ai.vision.model}")
  private String model;

  @Value("${ai.vision.timeoutSeconds:60}")
  private int timeoutSeconds;

  @Value("${ai.vision.retryCount:2}")
  private int retryCount;

  /**
   * 为 true 时跳过「官方 DeepSeek 文本模型不支持识图」的前置校验（例如自建代理已换成多模态模型）。
   */
  @Value("${ai.vision.skipDeepSeekTextOnlyCheck:false}")
  private boolean skipDeepSeekTextOnlyCheck;

  public DoubaoVisionClient(ObjectMapper objectMapper) {
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
    this.objectMapper = objectMapper;
  }

  @Override
  public String recognize(String prompt, String imageUrl) throws Exception {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("AI apiKey is empty. Please set DOUBAO_API_KEY.");
    }

    if (!skipDeepSeekTextOnlyCheck && isDeepSeekTextOnlyModel()) {
      throw new AppException(400,
        "当前 model 为 DeepSeek 纯文本模型（deepseek-chat / deepseek-reasoner），Chat API 仅支持纯文本，"
          + "不接受图片字段 image_url，因此无法做病虫害识图。第三方聚合网关即使不是 api.deepseek.com 也会如此。"
          + "请改用支持多模态的 endpoint 与视觉模型（火山方舟豆包视觉、OpenAI gpt-4o / gpt-4o-mini 等），并更新 ai.vision.endpoint 与 ai.vision.model。"
          + "若代理后实际已是多模态模型，可设置 ai.vision.skipDeepSeekTextOnlyCheck=true。");
    }

    Map<String, Object> req = new HashMap<>();
    req.put("model", model);
    // 使用标准多模态结构：文本 + 图片（data:image/...;base64,... 或公网 URL）
    req.put("messages", new Object[] {
      Map.of(
        "role", "user",
        "content", new Object[] {
          Map.of("type", "text", "text", prompt),
          Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
        }
      )
    });
    // 要求模型输出结构化 JSON（若API支持 response_format）
    req.put("response_format", Map.of("type", "json_object"));

    String body = objectMapper.writeValueAsString(req);

    Exception last = null;
    for (int i = 0; i <= retryCount; i++) {
      try {
        HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(endpoint))
          .timeout(Duration.ofSeconds(timeoutSeconds))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();

        HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
          String errBody = resp.body();
          if (resp.statusCode() == 400 && errBody != null
            && errBody.contains("image_url") && errBody.contains("expected `text`")) {
            throw new AppException(400,
              "上游接口按「仅文本消息」解析请求，拒绝了图片字段（image_url）。"
                + "请确认 ai.vision.endpoint 为支持视觉的 Chat Completions 地址，且 model 为多模态模型；"
                + "勿使用 deepseek-chat / deepseek-reasoner 等纯文本模型。"
                + "若图片为内网/预签名 URL，请改为在请求内使用 data:image/...;base64,...。");
          }
          throw new RuntimeException("AI request failed, status=" + resp.statusCode() + ", body=" + errBody);
        }

        JsonNode node = objectMapper.readTree(resp.body());
        // 常见 OpenAI 兼容：choices[0].message.content
        JsonNode content = node.at("/choices/0/message/content");
        if (!content.isMissingNode() && !content.isNull()) {
          return content.asText();
        }
        // 兜底：direct output
        JsonNode outputText = node.at("/output_text");
        if (!outputText.isMissingNode() && !outputText.isNull()) {
          return outputText.asText();
        }
        return resp.body();
      } catch (Exception e) {
        last = e;
        // 重试：指数退避简化处理
        Thread.sleep(300L * (i + 1));
      }
    }
    throw last;
  }

  /** DeepSeek 官方文本模型名；经聚合/镜像调用时 host 可能不是 api.deepseek.com，但行为相同。 */
  private boolean isDeepSeekTextOnlyModel() {
    if (model == null) {
      return false;
    }
    String m = model.trim().toLowerCase();
    return m.equals("deepseek-chat") || m.equals("deepseek-reasoner");
  }
}

