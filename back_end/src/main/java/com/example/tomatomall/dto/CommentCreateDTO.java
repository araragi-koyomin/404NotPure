package com.example.tomatomall.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CommentCreateDTO {
  private Integer userId;
  private String userName;
  private Integer productId;
  private String content;
  private Integer rate;
}