package com.example.tomatomall.controller;

import com.example.tomatomall.dto.CommentCreateDTO;
import com.example.tomatomall.po.Comment;
import com.example.tomatomall.service.CommentService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.CommentVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品评论管理控制器
 * 提供评论的创建、删除和查询功能
 */
@RestController
@RequestMapping("/api/products/")
public class CommentController {

  @Autowired
  CommentService commentService;

  /**
   * 创建商品评论
   * @param commentCreateDTO 评论创建数据传输对象
   * @return 操作结果
   */
  @PostMapping("/comments")
  public Response<String> createComment(@RequestBody CommentCreateDTO commentCreateDTO) {
    return Response.buildSuccess(commentService.createComment(commentCreateDTO));
  }

  /**
   * 删除商品评论
   * @param id 评论ID
   * @return 操作结果
   */
  @DeleteMapping("/comments/{id}")
  public Response<String> deleteComment(@PathVariable int id) {
    return Response.buildSuccess(commentService.deleteComment(id));
  }

  /**
   * 根据商品ID获取评论列表
   * @param productId 商品ID
   * @return 评论列表
   */
  @GetMapping("/{productId}/comments")
  public Response<List<CommentVO>> getCommentByProductId(@PathVariable int productId) {
    return Response.buildSuccess(commentService.getCommentByProductId(productId));
  }
}
