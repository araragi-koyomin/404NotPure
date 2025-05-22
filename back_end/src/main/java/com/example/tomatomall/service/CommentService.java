package com.example.tomatomall.service;

import com.example.tomatomall.dto.CommentCreateDTO;
import com.example.tomatomall.vo.CommentVO;

import java.util.List;

public interface CommentService {

  public String createComment(CommentCreateDTO commentCreateDTO);
  public String deleteComment(Integer id);
  public List<CommentVO> getCommentByProductId(Integer productId);
}
