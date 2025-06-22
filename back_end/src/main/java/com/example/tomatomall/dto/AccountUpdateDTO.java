package com.example.tomatomall.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;


@Getter
@Setter
@NoArgsConstructor
public class AccountUpdateDTO {
  @NotBlank(message = "用户名必填")
  private String username;

  private String password;

  private String name;

  @URL(message = "头像链接无效")
  private String avatar;

  @Pattern(regexp = "^1\\d{10}$", message = "手机号格式错误")
  private String telephone;

  @Email(message = "邮箱格式错误")
  private String email;

  private String location;

  private String role;
}
