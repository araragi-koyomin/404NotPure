package com.example.tomatomall.po;

import com.example.tomatomall.vo.AccountSimpleVO;
import com.example.tomatomall.vo.AccountVO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Integer id;

  @Basic
  @Column(name = "username", nullable = false, length = 50)
  private String username;

  @Basic
  @Column(name = "password", nullable = false, length = 100)
  private String password;

  @Basic
  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "role", nullable = false)
  private String role;

  @Basic
  @Column(name = "avatar", length = 255)
  private String avatar;

  @Basic
  @Column(name = "telephone", length = 11)
  private String telephone;

  @Basic
  @Column(name = "email", length = 100)
  private String email;

  @Basic
  @Column(name = "location", length = 255)
  private String location;

  public AccountVO toVO() {
    AccountVO vo = new AccountVO();
    vo.setId(this.id);
    vo.setUsername(this.username);
    vo.setName(this.name);
    vo.setAvatar(this.avatar);
    vo.setTelephone(this.telephone);
    vo.setEmail(this.email);
    vo.setLocation(this.location);
    vo.setPassword(this.password);
    vo.setRole(this.role);
    return vo;
  }

  public AccountSimpleVO toSimpleVO() {
    AccountSimpleVO vo = new AccountSimpleVO();
    vo.setUsername(this.username);
    vo.setName(this.name);
    vo.setRole(this.role);
    vo.setAvatar(this.avatar);
    vo.setTelephone(this.telephone);
    vo.setEmail(this.email);
    vo.setLocation(this.location);
    return vo;
  }
}