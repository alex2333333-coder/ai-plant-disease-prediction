package com.example.aiplantdisease.prompt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

@Service
public class PromptBuilder {

  @Value("${ai.prompt.file}")
  private String promptFile;

  public String buildPrompt(String cropType) {
    // 提示词工程：要求模型“仅输出结构化JSON”，并对防治建议做合规约束。
    String template = readTemplate();
    return template.replace("${CROP_CONTEXT}", buildCropContext(cropType));
  }

  /**
   * 多数场景用户无法事先知道作物种类：不强行把“未知”当成事实，而是让模型从图像推断。
   * 仅当用户明确填写了有效作物名时，才作为辅助线索（需与图像交叉验证）。
   */
  private static String buildCropContext(String cropType) {
    String t = cropType == null ? "" : cropType.trim();
    if (t.isEmpty() || isPlaceholderCropHint(t)) {
      return "【作物信息】调用方未提供可靠作物种类（或仅为占位）。你必须主要依据图片自行判断：结合植株形态、叶/花/果/茎、栽培环境等，"
          + "在 JSON 的 cropType 字段给出最可能的中文名称或合理类别（如「番茄」「茄科蔬菜」「观叶绿植」等）；"
          + "若信息不足，填「难以确定」并在 preventionAdvice 中简要说明依据与局限。禁止把「未知作物」等占位符原样写入 cropType。";
    }
    return "【作物信息】用户补充说明该植株可能为「" + t + "」。请结合图像核验：一致则采用；若图像明显不符，以图像为准写入 cropType，"
        + "并可在 preventionAdvice 中礼貌提示用户核对。";
  }

  private static boolean isPlaceholderCropHint(String t) {
    String n = t.replace(" ", "");
    return n.equalsIgnoreCase("未知")
        || n.equalsIgnoreCase("未知作物")
        || n.equals("不详")
        || n.equals("无")
        || n.equals("不确定")
        || n.equalsIgnoreCase("N/A")
        || n.equals("-");
  }

  private String readTemplate() {
    try {
      ClassPathResource resource = new ClassPathResource(promptFile);
      if (!resource.exists()) {
        // 兜底：内置最小提示词（方便首次跑通）
        return defaultTemplate();
      }
      try (Scanner scanner = new Scanner(resource.getInputStream(), StandardCharsets.UTF_8)) {
        scanner.useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : defaultTemplate();
      }
    } catch (Exception e) {
      return defaultTemplate();
    }
  }

  private String defaultTemplate() {
    return ""
      + "你是农业病虫害智能识别助手。\n"
      + "请基于提供的作物图片，完成识别与合规建议。\n\n"
      + "${CROP_CONTEXT}\n\n"
      + "要求输出：只输出一段严格JSON（不允许包含任何额外文字），JSON字段如下：\n"
      + "{\n"
      + "  \"isAlert\": boolean, \n"
      + "  \"cropType\": string,\n"
      + "  \"diseaseName\": string,\n"
      + "  \"hazardLevel\": string, \n"
      + "  \"preventionAdvice\": string\n"
      + "}\n\n"
      + "合规约束（必须遵守）：\n"
      + "- 防治建议不得推荐禁用、剧毒或高毒农药；不得给出可能违法/不安全的用药指引。\n"
      + "- 用药建议必须符合中国《农药管理条例》与NY/T 1276—2025农药安全使用规范。\n"
      + "- 建议优先采用农业防治、物理防治、生物防治与低风险合规药剂，并提示以当地植保部门推荐为准。\n"
      + "- 若无法明确判断，返回 isAlert=false，并说明“疑似但不确定”。\n";
  }
}

