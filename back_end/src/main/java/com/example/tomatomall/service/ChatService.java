package com.example.tomatomall.service;

import com.example.tomatomall.po.ChatSession;
import com.example.tomatomall.po.ChatMessage;

import java.util.List;

public interface ChatService {
    void sendMessage(ChatMessage message);
    List<ChatSession> getUserSessionsById(Integer userId);
}
