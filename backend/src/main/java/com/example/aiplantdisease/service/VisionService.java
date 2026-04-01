package com.example.aiplantdisease.service;

import com.example.aiplantdisease.ai.AiVisionClient;
import com.example.aiplantdisease.api.ApiResponse;
import com.example.aiplantdisease.dto.RecognizeRequest;
import com.example.aiplantdisease.dto.RecognizeResponse;
import com.example.aiplantdisease.dto.RecognitionRecordDto;
import com.example.aiplantdisease.entity.RecognitionRecord;
import com.example.aiplantdisease.exception.AppException;
import com.example.aiplantdisease.prompt.PromptBuilder;
import com.example.aiplantdisease.repository.RecognitionRecordRepository;
import com.example.aiplantdisease.storage.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class VisionService {

  private final RecognitionRecordRepository recordRepository;
  private final MinioStorageService minioStorageService;
  private final AiVisionClient aiVisionClient;
  private final PromptBuilder promptBuilder;
  private final ObjectMapper objectMapper;

  // 已固定走 base64 内嵌图片；原开关保留便于恢复「用 MinIO 预签名 URL 调模型」。
  // @Value("${ai.vision.imageInlineBase64:false}")
  // private boolean imageInlineBase64;

  public VisionService(
    RecognitionRecordRepository recordRepository,
    MinioStorageService minioStorageService,
    AiVisionClient aiVisionClient,
    PromptBuilder promptBuilder,
    ObjectMapper objectMapper
  ) {
    this.recordRepository = recordRepository;
    this.minioStorageService = minioStorageService;
    this.aiVisionClient = aiVisionClient;
    this.promptBuilder = promptBuilder;
    this.objectMapper = objectMapper;
  }

  public RecognizeResponse recognize(RecognizeRequest request) throws Exception {
    if (request == null || request.getImageBase64() == null) {
      throw new AppException(400, "imageBase64 is required");
    }

    byte[] rawBytes = decodeBase64(request.getImageBase64());
    if (rawBytes.length > 5 * 1024 * 1024) {
      throw new AppException(400, "单张图片大小限制<=5MB");
    }

    ImagePayload payload = validateAndConvertToJpeg(rawBytes);

    // 4) 上传 MinIO，生成唯一Key
    String objectKey = minioStorageService.buildObjectKey();
    minioStorageService.uploadJpg(objectKey, payload.jpegBytes, "image/jpeg");
    // 临时签名 URL：给前端展示/历史记录用（不再传给豆包，内网 MinIO 地址常被判定无效）
    String presignedUrl = minioStorageService.generatePresignedUrl(objectKey);

    // 5) 拼接专业 Prompt + 图片，调用多模态大模型（固定 data URL base64）
    String cropType = request.getCropType();
    if (cropType == null || cropType.isBlank()) cropType = "未知作物";
    String prompt = promptBuilder.buildPrompt(cropType);

    String imageForAi = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(payload.jpegBytes);
    // 原：用 MinIO 预签名 URL 传给模型（外网不可达会 400 InvalidParameter URL）
    // String imageForAi = imageInlineBase64
    //   ? ("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(payload.jpegBytes))
    //   : presignedUrl;
    String aiText = aiVisionClient.recognize(prompt, imageForAi);
    // 6) 解析结构化结果：解析失败要兜底
    ParsedResult parsed = parseAiResult(aiText);

    // 7) 入库与告警标记
    RecognitionRecord record = new RecognitionRecord();
    record.setId(UUID.randomUUID().toString());
    record.setCropType(parsed.cropType != null ? parsed.cropType : cropType);
    record.setDiseaseName(parsed.diseaseName != null ? parsed.diseaseName : "无法识别");
    record.setHazardLevel(parsed.hazardLevel != null ? parsed.hazardLevel : "未知");
    record.setPreventionAdvice(parsed.preventionAdvice);
    record.setImageKey(objectKey);
    record.setAlert(parsed.isAlert);
    record.setCreateTime(Instant.now());

    recordRepository.save(record);

    // 返回给前端：识别结果 + 图片临时地址
    RecognizeResponse resp = new RecognizeResponse();
    resp.setRecordId(record.getId());
    resp.setAlert(record.isAlert());
    resp.setCropType(record.getCropType());
    resp.setDiseaseName(record.getDiseaseName());
    resp.setHazardLevel(record.getHazardLevel());
    resp.setPreventionAdvice(record.getPreventionAdvice());
    resp.setImageUrl(presignedUrl);
    return resp;
  }

  public List<RecognitionRecordDto> listLatest(int limit) {
    int safe = Math.max(1, Math.min(limit, 50));
    List<RecognitionRecord> top = recordRepository.findTop20ByOrderByCreateTimeDesc();
    List<RecognitionRecordDto> out = new ArrayList<>();
    for (RecognitionRecord r : top) {
      RecognitionRecordDto dto = new RecognitionRecordDto();
      dto.setId(r.getId());
      dto.setCropType(r.getCropType());
      dto.setDiseaseName(r.getDiseaseName());
      dto.setHazardLevel(r.getHazardLevel());
      dto.setPreventionAdvice(r.getPreventionAdvice());
      dto.setAlert(r.isAlert());
      dto.setCreateTime(r.getCreateTime());
      try {
        dto.setImageUrl(minioStorageService.generatePresignedUrl(r.getImageKey()));
      } catch (Exception ignored) {
        // 若签名URL失败，仍返回其它信息。
      }
      out.add(dto);
      if (out.size() >= safe) break;
    }
    return out;
  }

  private byte[] decodeBase64(String dataUrlOrBase64) {
    String s = dataUrlOrBase64.trim();
    int idx = s.indexOf(',');
    if (idx >= 0) s = s.substring(idx + 1);
    // 允许前端传 "data:image/jpeg;base64,xxx"
    return Base64.getDecoder().decode(s);
  }

  private ImagePayload validateAndConvertToJpeg(byte[] rawBytes) throws Exception {
    boolean isJpeg = isJpeg(rawBytes);
    boolean isPng = isPng(rawBytes);
    if (!isJpeg && !isPng) {
      throw new AppException(400, "仅允许 jpg/png/jpeg 格式图片");
    }
    if (isJpeg) {
      return new ImagePayload(rawBytes, true);
    }
    // PNG -> JPG（保证上传Key 以 .jpg 结尾，并规避路径/扩展问题）
    BufferedImage img = ImageIO.read(new ByteArrayInputStream(rawBytes));
    if (img == null) throw new AppException(400, "图片解码失败");
    byte[] jpegBytes = toJpeg(img, 0.86f);
    return new ImagePayload(jpegBytes, true);
  }

  private boolean isJpeg(byte[] bytes) {
    if (bytes.length < 3) return false;
    return (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
  }

  private boolean isPng(byte[] bytes) {
    byte[] sig = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
    if (bytes.length < sig.length) return false;
    for (int i = 0; i < sig.length; i++) {
      if (bytes[i] != sig[i]) return false;
    }
    return true;
  }

  private byte[] toJpeg(BufferedImage image, float quality) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
      writer.setOutput(ios);
      ImageWriteParam param = writer.getDefaultWriteParam();
      // 某些ImageWriteParam类型不支持quality设置时会直接忽略
      if (param instanceof javax.imageio.plugins.jpeg.JPEGImageWriteParam jpegParam) {
        jpegParam.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        jpegParam.setCompressionQuality(quality);
      }
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return baos.toByteArray();
  }

  private ParsedResult parseAiResult(String aiText) {
    try {
      if (aiText == null || aiText.isBlank()) {
        return new ParsedResult(false, null, null, null, null);
      }
      String trimmed = aiText.trim();
      // 有些模型返回带前后缀文本：尝试提取第一个 { ... } JSON 对象片段。
      int l = trimmed.indexOf('{');
      int r = trimmed.lastIndexOf('}');
      String jsonPart = (l >= 0 && r > l) ? trimmed.substring(l, r + 1) : trimmed;
      JsonNode node = objectMapper.readTree(jsonPart);

      boolean isAlert = node.path("isAlert").asBoolean(false);
      String cropType = node.path("cropType").isMissingNode() ? null : node.path("cropType").asText(null);
      String diseaseName = node.path("diseaseName").isMissingNode() ? null : node.path("diseaseName").asText(null);
      String hazardLevel = node.path("hazardLevel").isMissingNode() ? null : node.path("hazardLevel").asText(null);
      String preventionAdvice = node.path("preventionAdvice").isMissingNode() ? null : node.path("preventionAdvice").asText(null);

      return new ParsedResult(isAlert, cropType, diseaseName, hazardLevel, preventionAdvice);
    } catch (Exception e) {
      // 识别失败兜底：记录为非告警
      return new ParsedResult(false, null, null, null, "识别失败：请检查模型输出格式或稍后重试");
    }
  }

  private static class ImagePayload {
    private final byte[] jpegBytes;
    private final boolean ok;

    private ImagePayload(byte[] jpegBytes, boolean ok) {
      this.jpegBytes = jpegBytes;
      this.ok = ok;
    }
  }

  private static class ParsedResult {
    private final boolean isAlert;
    private final String cropType;
    private final String diseaseName;
    private final String hazardLevel;
    private final String preventionAdvice;

    private ParsedResult(boolean isAlert, String cropType, String diseaseName, String hazardLevel, String preventionAdvice) {
      this.isAlert = isAlert;
      this.cropType = cropType;
      this.diseaseName = diseaseName;
      this.hazardLevel = hazardLevel;
      this.preventionAdvice = preventionAdvice;
    }
  }
}

