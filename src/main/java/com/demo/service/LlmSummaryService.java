package com.demo.service;

import com.demo.memory.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmSummaryService {

    private final ChatLanguageModel chatLanguageModel;

    /**
     * 对历史对话做精简摘要，保留关键问题与偏好信息。
     */
    public String generateSummary(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对以下图书助手对话进行精简摘要，保留关键问题、用户偏好、已给出的结论与技术要点，控制在 300 字以内：\n");

        for (ChatMessage msg : messages) {
            if (msg == null) continue;
            sb.append(msg.getRole()).append("：").append(msg.getContent()).append("\n");
        }

        return chatLanguageModel.generate(sb.toString());
    }
}

