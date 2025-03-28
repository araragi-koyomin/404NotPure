package com.example.tomatomall.service;


import com.example.tomatomall.dto.AccountUpdateDTO;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.vo.AccountSimpleVO;
import com.example.tomatomall.vo.AccountVO;


public interface AccountService {


    public String register(Account account);


    public String login(AccountVO accountVO);

    public AccountSimpleVO getAccount(String username);

    public String update(AccountUpdateDTO accountUpdateDTO);

}
