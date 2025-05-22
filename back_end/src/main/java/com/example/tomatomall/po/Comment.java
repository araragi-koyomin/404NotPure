package com.example.tomatomall.po;

import com.example.tomatomall.vo.CommentVO;
import lombok.*;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Getter
@Setter
@Table(name = "comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private int id;

  private Integer userId;
  private String userName;
  private Integer productId;
  private String content;
  private Integer rate;
  private Timestamp createTime;

  public CommentVO toVO() {
    CommentVO commentVO = new CommentVO();
    commentVO.setId(id);
    commentVO.setUserId(userId);
    commentVO.setUserName(userName);
    commentVO.setContent(content);
    commentVO.setProductId(productId);
    commentVO.setCreateTime(createTime);
    commentVO.setRate(rate);

    return commentVO;
  }
}
