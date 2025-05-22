package com.example.tomatomall.vo;

import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;

@Data
public class CommentVO implements Serializable {
  private Integer id;
  private Integer userId;
  private String userName;
  private Integer productId;
  private String content;
  private Integer rate;
  private Timestamp createTime;
}