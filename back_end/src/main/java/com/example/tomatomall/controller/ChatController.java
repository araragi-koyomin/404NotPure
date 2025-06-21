package com.example.tomatomall.controller;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.ChatMessage;
import com.example.tomatomall.po.ChatSession;
import com.example.tomatomall.repository.ChatMessageRepository;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.ChatService;
import com.example.tomatomall.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
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

    @PostMapping("/question")
    public void question(HttpServletRequest request){
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())){
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            throw TomatoException.notLogin();
        }
        Integer userId = tokenUtil.getUserIdfromToken(token);
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

    // 发送消息
    @PostMapping("/send")
    public void sendMessage(@RequestBody ChatMessage message, HttpServletRequest request) {
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())){
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            throw TomatoException.notLogin();
        }
        Integer senderId = tokenUtil.getUserIdfromToken(token);
        message.setSenderId(senderId);
        chatService.sendMessage(message);
    }

    // 获取聊天记录（对话）
    @GetMapping("/messages")
    public List<ChatMessage> getMessages(@RequestParam Integer peerId, HttpServletRequest request) {
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            throw TomatoException.notLogin();
        }
        Integer userId = tokenUtil.getUserIdfromToken(token);
        return chatMessageRepository.findConversation(userId, peerId);
    }

    // 获取当前用户的会话列表
    @GetMapping("/sessions")
    public List<ChatSession> getSessions(HttpServletRequest request) {
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            throw TomatoException.notLogin();
        }
        Integer userId = tokenUtil.getUserIdfromToken(token);
        return chatService.getUserSessions(userId);
    }
}
