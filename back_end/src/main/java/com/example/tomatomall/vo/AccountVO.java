package com.example.tomatomall.vo;

import com.example.tomatomall.po.Account;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class AccountVO {

  private Integer id;

  private String username;

  private String name;

  private String role;

  private String avatar;

  private String telephone;

  private String email;

  private String location;

  private String password;

  public Account toPO() {
    Account account = new Account();
    account.setId(id);
    account.setUsername(username);
    account.setName(name);
    account.setRole(role);
    account.setAvatar(avatar);
    account.setTelephone(telephone);
    account.setEmail(email);
    account.setLocation(location);
    account.setPassword(password);
    return account;
  }

}