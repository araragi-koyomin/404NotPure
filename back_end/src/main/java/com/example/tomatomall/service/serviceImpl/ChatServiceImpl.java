package com.example.tomatomall.service.serviceImpl;


import com.example.tomatomall.po.ChatMessage;
import com.example.tomatomall.po.ChatSession;
import com.example.tomatomall.repository.ChatMessageRepository;
import com.example.tomatomall.repository.ChatSessionRepository;
import com.example.tomatomall.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {
        @Autowired
        private ChatMessageRepository messageRepo;

        @Autowired
        private ChatSessionRepository sessionRepo;

        @Override
        public void sendMessage(ChatMessage message) {
            message.setTimestamp(System.currentTimeMillis());
            messageRepo.save(message);

            // 更新 sender 的会话记录
            updateSession(message.getSenderId(), message.getReceiverId(), message.getContent(), message.getTimestamp());

            // 更新 receiver 的会话记录
            updateSession(message.getReceiverId(), message.getSenderId(), message.getContent(), message.getTimestamp());
        }

        private void updateSession(Integer userId, Integer peerId, String content, Long timestamp) {
            ChatSession session = sessionRepo.findByUserIdAndPeerId(userId, peerId);
            if (session == null) {
                session = new ChatSession();
            }
            session.setUserId(userId);
            session.setPeerId(peerId);
            session.setLastMessage(content);
            session.setLastTimestamp(timestamp);
            sessionRepo.save(session);
        }

        @Override
        public List<ChatSession> getUserSessions(Integer userId){
            List<ChatSession> sessions = sessionRepo.findByUserIdOrderByLastTimestampDesc(userId);
            if (sessions == null) {
                throw new RuntimeException();
            }
            return sessions;
        }
}
