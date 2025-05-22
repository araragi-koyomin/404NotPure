package com.example.tomatomall.controller;

import com.example.tomatomall.dto.CommentCreateDTO;
import com.example.tomatomall.po.Comment;
import com.example.tomatomall.service.CommentService;
import com.example.tomatomall.vo.CommentVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products/")
public class CommentController {

  @Autowired
  CommentService commentService;

  @PostMapping("/comments")
  public Response<String> createComment(@RequestBody CommentCreateDTO commentCreateDTO) {
    return Response.buildSuccess(commentService.createComment(commentCreateDTO));
  }

  @DeleteMapping("/comments/{id}")
  public Response<String> deleteComment(@PathVariable int id) {
    return Response.buildSuccess(commentService.deleteComment(id));
  }

  @GetMapping("/{productId}/comments")
  public Response<List<CommentVO>> getCommentByProductId(@PathVariable int productId) {
    return Response.buildSuccess(commentService.getCommentByProductId(productId));
  }
}
