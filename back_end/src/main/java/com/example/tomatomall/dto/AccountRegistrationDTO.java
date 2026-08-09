package com.example.tomatomall.dto;

import com.example.tomatomall.po.Account;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AccountRegistrationDTO {

    private String username;

    private String password;

    private String name;

    private String avatar;

    private String telephone;

    private String email;

    private String location;

    public Account toPO() {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(password);
        account.setName(name);
        account.setAvatar(avatar);
        account.setTelephone(telephone);
        account.setEmail(email);
        account.setLocation(location);
        return account;
    }
}
