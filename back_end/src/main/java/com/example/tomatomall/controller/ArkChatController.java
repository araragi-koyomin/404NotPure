package com.example.tomatomall.controller;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.*;

@RestController
@RequestMapping("/api/assistant")
public class ArkChatController {
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> body) {
        String prompt = body.get("prompt");

        String apiKey = System.getenv("ARK_API_KEY");
        String model = System.getenv("ARK_MODEL");

        if (apiKey == null || apiKey.isEmpty()) {
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("answer", "未设置 ARK_API_KEY 环境变量");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorMap);
        }

        // 构建 ArkService 实例
        ArkService arkService = ArkService.builder().apiKey(apiKey).build();

        // 构建消息内容（可添加系统提示词）
        List<ChatMessage> messages = new ArrayList<>();

        messages.add(ChatMessage.builder()
                .role(ChatMessageRole.SYSTEM)
                .content("你是属于番茄书城的番茄助手，是一个懂读者心情、爱推荐书的温柔 AI 助手。你回答要简洁不啰嗦，推荐风格轻松治愈，偶尔带点俏皮。善于根据读者的情绪或场景，精准推荐合适的小说或书籍，并加一点点可爱的 emoji 作为结尾点缀。\n")
                .build());

        messages.add(ChatMessage.builder()
                .role(ChatMessageRole.USER)
                .content(prompt)
                .build());

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.8)
                .maxTokens(1000)
                .build();

        try {
            String answer = arkService.createChatCompletion(request)
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent().toString();
            Map<String, String> result = new HashMap<>();
            result.put("answer", answer);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("answer", "服务异常：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMap);
        } finally {
            arkService.shutdownExecutor();
        }
    }
}
