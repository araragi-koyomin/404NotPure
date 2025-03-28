package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.AccountUpdateDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.AccountService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.AccountSimpleVO;
import com.example.tomatomall.vo.AccountVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AccountServiceImpl implements AccountService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;


    public String register(Account account) {
        // 检查用户名是否已存在
        Optional<Account> existingAccount = userRepository.findByUsername(account.getUsername());
        if (existingAccount.isPresent()) {
            throw TomatoException.userNameExist();
        }
        // 加密密码
        account.setPassword(passwordEncoder.encode(account.getPassword()));
        userRepository.save(account);
        return "注册成功";
    }

    //支持手机号和用户名登录
    public String login(AccountVO accountVO) {
        Account tmp=accountVO.toPO();
        String usernameOrTelephone = tmp.getUsername();
        String rawPassword = tmp.getPassword();
        Optional<Account> account = userRepository.findByUsername(usernameOrTelephone);
        if (!account.isPresent()) {
            account = userRepository.findByTelephone(usernameOrTelephone);
        }
        if (account.isPresent() && passwordEncoder.matches(rawPassword, account.get().getPassword())) {
            return tokenUtil.generateToken(account.get().getId()); // 用 id 生成 token
        }
        throw TomatoException.loginFailure();
    }

    public AccountSimpleVO getAccount(String username) {
        Optional<Account> account = !userRepository.findByUsername(username).isPresent() ? userRepository.findByTelephone(username) : userRepository.findByUsername(username);
        if (!account.isPresent()) {
            throw TomatoException.userNotExist();
        }
        if(account.get()==null){
            throw TomatoException.userNotExist();
        }
        return account.get().toSimpleVO();
    }

    @Override
    public String update(AccountUpdateDTO accountUpdateDTO) {
        Account account = userRepository.findByUsername(accountUpdateDTO.getUsername()).orElseThrow(TomatoException::userNotExist);
        if (accountUpdateDTO.getPassword() != null) account.setPassword(passwordEncoder.encode(accountUpdateDTO.getPassword()));
        if (accountUpdateDTO.getName() != null) account.setName(accountUpdateDTO.getName());
        if (accountUpdateDTO.getAvatar() != null) account.setAvatar(accountUpdateDTO.getAvatar());
        if (accountUpdateDTO.getTelephone() != null) account.setTelephone(accountUpdateDTO.getTelephone());
        if (accountUpdateDTO.getEmail() != null) account.setEmail(accountUpdateDTO.getEmail());
        if (accountUpdateDTO.getLocation() != null) account.setLocation(accountUpdateDTO.getLocation());
        if (accountUpdateDTO.getRole() != null) account.setRole(accountUpdateDTO.getRole());

        userRepository.save(account);
        return "用户信息已更新";
    }
}
