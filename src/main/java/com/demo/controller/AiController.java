package com.demo.controller;

import com.demo.common.AppConstants;
import com.demo.service.AiService;
import com.smartlibrary.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @GetMapping("/analyze")
    public Result<String> analyze(@RequestParam(value = "question") String question,
                                  @RequestParam(value = "debug", required = false,
                                          defaultValue = AppConstants.API_AI_ANALYZE_DEBUG_DEFAULT) boolean debug) {
        return aiService.analyze(question, debug);
    }
}

