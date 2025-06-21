package com.example.tomatomall.repository;

import com.example.tomatomall.po.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByReceiverIdAndIsReadFalse(Integer userId);
    //List<ChatMessage> findBySenderIdAndReceiverIdOrderByTimestampAsc(Integer senderId, Integer receiverId);

    @Query("FROM ChatMessage m WHERE (m.senderId = :uid1 AND m.receiverId = :uid2) OR (m.senderId = :uid2 AND m.receiverId = :uid1) ORDER BY m.timestamp")
    List<ChatMessage> findConversation(@Param("uid1") Integer uid1, @Param("uid2") Integer uid2);
}
