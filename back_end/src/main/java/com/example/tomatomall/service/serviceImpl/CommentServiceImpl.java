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

@Service
public class CommentServiceImpl implements CommentService {

  @Autowired
  CommentRepository commentRepository;

  @Autowired
  ProductRepository productRepository;

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
