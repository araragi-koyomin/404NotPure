package com.example.tomatomall.controller;

import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.ChatMessage;
import com.example.tomatomall.po.ChatSession;
import com.example.tomatomall.repository.ChatMessageRepository;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.ChatService;
import com.example.tomatomall.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    private UserRepository userRepository;

    /**
     * 主页进入咨询状态，并随机选择一个管理员进行对话
     * @param request HTTP请求
     */
    @PostMapping("/question")
    public void question(HttpServletRequest request){
        Integer userId = TokenUtil.getUserIdFromRequest(request);
        List<Account> admins = userRepository.findByRole("admin");

        if (admins.isEmpty()) {
            throw new RuntimeException("无客服在线");
        }

        Account admin = admins.stream()
                .filter(a -> !a.getId().equals(userId))
                .skip(new Random().nextInt((int) admins.size()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("无可用客服"));

        int server_id = admin.getId();
        ChatMessage message=new ChatMessage();
        message.setSenderId(userId);
        message.setReceiverId(server_id);
        message.setContent("你好！");
        chatService.sendMessage(message);
    }

    /**
     * 发送信息
     * @param message 消息实体
     * @param request HTTP请求
     */
    @PostMapping("/send")
    public void sendMessage(@RequestBody ChatMessage message, HttpServletRequest request) {
        Integer senderId = TokenUtil.getUserIdFromRequest(request);
        message.setSenderId(senderId);
        chatService.sendMessage(message);
    }

    /**
     * 获取聊天记录（对话）
     * @param peerId 伙伴id
     * @param request HTTP请求
     * @return
     */
    @GetMapping("/messages")
    public List<ChatMessage> getMessages(@RequestParam Integer peerId, HttpServletRequest request) {
        Integer userId = TokenUtil.getUserIdFromRequest(request);
        return chatMessageRepository.findConversation(userId, peerId);
    }

    /**
     * 获取当前用户的会话列表
     * @param request HTTP请求
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public List<ChatSession> getSessions(HttpServletRequest request) {
        Integer userId = TokenUtil.getUserIdFromRequest(request);
        return chatService.getUserSessionsById(userId);
    }
}
