package com.example.tomatomall.controller;

import com.example.tomatomall.dto.AccountUpdateDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.AccountService;
import com.example.tomatomall.vo.AccountSimpleVO;
import com.example.tomatomall.vo.AccountVO;
import com.example.tomatomall.vo.Response;
import com.example.tomatomall.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Resource
    AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    /**
     * 获取用户详情
     */
    @GetMapping("/{username}")
    public Response getUser(@PathVariable String username) {
        AccountSimpleVO accountVO = accountService.getAccount(username);
        return Response.buildSuccess(accountVO);
    }

    /**
     * 创建新的用户
     */
    @PostMapping()
    public Response<String> createUser(@RequestBody AccountVO accountVO) {
        return Response.buildSuccess(accountService.register(accountVO.toPO()));
    }

    /**
     * 更新用户信息
     */
    @PutMapping()
    public Response<String> updateUser(@RequestBody AccountUpdateDTO dto, HttpServletRequest request) {
        String token = request.getHeader("token");

        if (token == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("token".equals(cookie.getName())) {
                        token = cookie.getValue();
                        System.out.println("已获取token");
                        break;
                    }
                }
            }
        }

//      3. Token 不存在 → 返回 401 未授权
        if (token == null) {
            throw TomatoException.notLogin();
        }

        // 4. 解析 Token 获取当前用户（假设 Token 直接存储用户名）
        Integer accountId;
        try {
            accountId = TokenUtil.getUserIdFromToken(token); // 现在是取出用户 ID
            System.out.println("Account ID from Token: " + accountId);
        } catch (Exception e) {
            throw TomatoException.notLogin();
        }

        // 通过 ID 获取最新的账号信息
        Account currentAccount = userRepository.findById(accountId)
                .orElseThrow(TomatoException::notLogin);

        System.out.println("Username from DTO: " + dto.getUsername());
        System.out.println("Username from Account: " + currentAccount.getUsername());

        // 校验前端传过来的 username 是否是自己的，防止越权修改
        if (!dto.getUsername().equals(currentAccount.getUsername())) {
            throw TomatoException.noPermission();
        }
        return Response.buildSuccess(accountService.update(dto));
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Response<String> login(@RequestBody AccountVO accountVO, HttpServletResponse response) {
        String token = accountService.login(accountVO);
        // 将Token设置到Cookie中
        Cookie cookie = new Cookie("token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(cookie);
        return Response.buildSuccess(token);
    }
}
