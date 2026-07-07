package com.wujia.pet.controller;

import com.wujia.pet.service.PetAiService;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class QaController {

    private final PetAiService petAiService;

    public QaController(PetAiService petAiService) {
        this.petAiService = petAiService;
    }

    @GetMapping("/qa")
    public String qa() {
        return "qa";
    }

    @PostMapping("/qa/ask")
    @ResponseBody
    public Map<String, String> ask(@RequestBody QuestionRequest request) {
        String question = request == null || request.question() == null ? "" : request.question().trim();
        if (question.isBlank()) {
            return Map.of("answer", "先输入一个宠物问题，我会尽量帮你梳理。");
        }
        return Map.of("answer", petAiService.answer(question));
    }

    private record QuestionRequest(String question) {
    }
}
