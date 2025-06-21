package com.example.tomatomall.repository;

import com.example.tomatomall.po.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUserIdOrderByLastTimestampDesc(Integer userId);
    ChatSession findByUserIdAndPeerId(Integer userId, Integer peerId);
}
