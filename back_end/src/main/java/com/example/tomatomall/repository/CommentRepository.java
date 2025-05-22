package com.example.tomatomall.repository;

import com.example.tomatomall.po.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Integer> {
  Comment findByUserIdAndProductId(int userId, int productId);
  List<Comment> findByProductId(int productId);
}
