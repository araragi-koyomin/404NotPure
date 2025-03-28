package com.example.tomatomall.vo;
import com.example.tomatomall.po.Account;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

//这个类是暴露给用户的用户信息
@Getter
@Setter
@NoArgsConstructor
public class AccountSimpleVO {
    private String username;
    private String name;
    private String role;
    private String avatar;
    private String telephone;
    private String email;
    private String location;
}
