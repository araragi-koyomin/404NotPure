package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.CommentCreateDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Carts;
import com.example.tomatomall.po.Comment;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.repository.CommentRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.service.CommentService;
import com.example.tomatomall.vo.CommentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 评论服务实现类
 * 处理商品评论的增删改查
 */
@Service
public class CommentServiceImpl implements CommentService {

  @Autowired
  CommentRepository commentRepository;

  @Autowired
  ProductRepository productRepository;

  /**
   * 创建评论
   * @param commentCreateDTO 评论创建DTO
   * @return 操作结果
   * @throws TomatoException 评论已存在或商品不存在时抛出
   */
  @Override
  public String createComment(CommentCreateDTO commentCreateDTO) {
    Comment comment = commentRepository.findByUserIdAndProductId(commentCreateDTO.getUserId(), commentCreateDTO.getProductId());
    if (comment != null) {
      throw TomatoException.commentExist();
    }

    Comment newComment = new Comment();
    newComment.setUserId(commentCreateDTO.getUserId());
    newComment.setUserName(commentCreateDTO.getUserName());
    newComment.setContent(commentCreateDTO.getContent());
    newComment.setProductId(commentCreateDTO.getProductId());
    newComment.setRate(commentCreateDTO.getRate());
    newComment.setCreateTime(new Timestamp(System.currentTimeMillis()));

    commentRepository.save(newComment);
    return "创建成功";
  }

  /**
   * 删除评论
   * @param id 评论ID
   * @return 操作结果
   * @throws TomatoException 评论不存在时抛出
   */
  @Override
  public String deleteComment(Integer id) {
    Optional<Comment> commentOptional = commentRepository.findById(id);
    if (commentOptional.isPresent()) {
      commentRepository.deleteById(id);
      return "删除成功";
    } else {
      throw TomatoException.commentNotExist();
    }
  }

  /**
   * 获取商品评论列表
   * @param productId 商品ID
   * @return 评论视图对象列表
   * @throws TomatoException 商品不存在时抛出
   */
  @Override
  public List<CommentVO> getCommentByProductId(Integer productId) {
    Optional<Product> productOptional = productRepository.findById(productId);
    if (productOptional.isPresent()) {
      List<Comment> comments = commentRepository.findByProductId(productId);
      return comments.stream().map(Comment::toVO).collect(Collectors.toList());
    } else {
      throw TomatoException.productNotExist();
    }
  }
}
