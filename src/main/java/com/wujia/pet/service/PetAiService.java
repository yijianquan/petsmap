package com.wujia.pet.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PetAiService {

    private final RestClient restClient;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.base-url:https://api.siliconflow.cn/v1/chat/completions}")
    private String baseUrl;

    @Value("${app.ai.model:Qwen/Qwen2.5-7B-Instruct}")
    private String model;

    public PetAiService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String answer(String question) {
        if (question == null || question.isBlank()) {
            return "先输入一个宠物问题，我会尽量帮你梳理。";
        }
        String cleanQuestion = question.trim();
        if (apiKey != null && !apiKey.isBlank()) {
            String aiAnswer = askQwen(cleanQuestion);
            if (aiAnswer != null && !aiAnswer.isBlank()) {
                return aiAnswer;
            }
        }
        return fallbackAnswer(cleanQuestion);
    }

    private String askQwen(String question) {
        Map<String, Object> request = Map.of(
                "model", model,
                "temperature", 0.4,
                "max_tokens", 800,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "你是“吾家有宠”的宠物健康与养护问答助手。请用简洁中文回答，先给可执行建议。遇到急症、疑似中毒、持续呕吐腹泻、呼吸困难、抽搐、便血、精神极差等情况，明确建议尽快联系宠物医院。不要编造诊断，不要替代兽医。"),
                        Map.of("role", "user", "content", question)));
        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                return null;
            }
            return response.path("choices").path(0).path("message").path("content").asText(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String fallbackAnswer(String question) {
        String lower = question.toLowerCase();
        if (containsAny(lower, "呕吐", "吐", "腹泻", "拉稀", "便血", "抽搐", "中毒", "呼吸", "不吃")) {
            return "这类情况可能需要尽快就医。你可以先记录宠物年龄、体重、症状开始时间、呕吐/排便次数、是否误食，并联系附近宠物医院。若出现持续呕吐、便血、抽搐、呼吸困难或精神很差，建议直接急诊。";
        }
        if (containsAny(lower, "疫苗", "免疫", "狂犬", "猫三联", "犬四联")) {
            return "疫苗建议按宠物年龄、既往免疫记录和医生建议安排。常见做法是幼宠完成基础免疫后定期加强，狂犬疫苗按当地规定接种。接种前后观察精神、食欲和过敏反应，身体不适时先咨询医生。";
        }
        if (containsAny(lower, "洗澡", "驱虫", "跳蚤", "蜱", "耳朵", "指甲")) {
            return "日常护理可以按宠物皮肤状态和活动频率调整。洗澡不宜过频，驱虫要区分体内外并按体重用药。耳朵有异味、红肿、频繁抓挠，或皮肤出现大片脱毛结痂时，建议先做检查再用药。";
        }
        if (containsAny(lower, "吃", "粮", "喂", "零食", "体重", "减肥")) {
            return "饮食先看年龄、体重、绝育状态和活动量。主粮尽量稳定，换粮用 7 天左右逐步过渡。零食控制在每日热量的一小部分，突然不吃、暴瘦或暴胖都建议排查健康原因。";
        }
        return "可以从这几个方面判断：宠物年龄和品种、症状持续多久、精神食欲是否变化、是否接种/驱虫、最近有没有换粮或外出。你补充这些信息后，我可以继续帮你整理更具体的处理建议。";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
