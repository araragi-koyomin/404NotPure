package com.example.tomatomall.controller;

import com.example.tomatomall.dto.AccountPointsUpdateDTO;
import com.example.tomatomall.dto.AccountRegistrationDTO;
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
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Resource
    AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    /**
     * 根据用户名获取用户信息
     * @param username 用户名
     * @return 用户详细信息
     */
    @GetMapping("/{username}")
    public Response<AccountSimpleVO> getAccountByUsername(@PathVariable String username) {
        AccountSimpleVO accountVO = accountService.getAccount(username);
        return Response.buildSuccess(accountVO);
    }

    /**
     * 创建新账户
     * @param registration 公开注册允许提交的账户信息
     * @return 操作结果
     */
    @PostMapping()
    public Response<String> createAccount(@RequestBody AccountRegistrationDTO registration) {
        return Response.buildSuccess(accountService.register(registration.toPO()));
    }

    /**
     * 更新账户信息
     * @param dto 账户更新数据传输对象
     * @param request HTTP请求对象
     * @return 操作结果
     * @throws TomatoException 未登录或权限不足时抛出
     */
    @PutMapping()
    public Response<String> updateAccount(@RequestBody AccountUpdateDTO dto, HttpServletRequest request) {
        String token = TokenUtil.extractTokenFromRequest(request);
        if (token == null) {
            throw TomatoException.notLogin();
        }
        // 解析 Token 获取当前用户（假设 Token 直接存储用户名）
        Integer accountId = TokenUtil.getUserIdFromToken(token);
        // 通过 ID 获取最新的账号信息
        Account currentAccount = userRepository.findById(accountId)
                .orElseThrow(TomatoException::notLogin);
        // 校验前端传过来的 username 是否是自己的，防止越权修改
        if (!dto.getUsername().equals(currentAccount.getUsername())) {
            throw TomatoException.noPermission();
        }
        return Response.buildSuccess(accountService.update(dto));
    }

    /**
     * 账户登录
     * @param accountVO 账户信息视图对象
     * @param response HTTP响应对象
     * @return 认证令牌
     */
    @PostMapping("/login")
    public Response<String> login(@RequestBody AccountVO accountVO, HttpServletResponse response) {
        String token = accountService.login(accountVO);
        // 将Token设置到Cookie中
        TokenUtil.setTokenToCookie(response, token);
        return Response.buildSuccess(token);
    }

    /**
     * 获取账户积分
     * @param username 用户名
     * @return 积分数量
     */
    @GetMapping("/{username}/points")
    public Response<Integer> getAccountPoints(@PathVariable String username) {
        return Response.buildSuccess(accountService.getUserPoints(username));
    }

    /**
     * 更新账户积分
     * @param username 用户名
     * @param dto 积分更新数据传输对象
     * @return 操作结果
     */
    @PatchMapping("/{username}/points")
    public Response<String> updateAccountPoints(@PathVariable String username, @RequestBody AccountPointsUpdateDTO dto) {
        String result = accountService.updateUserPoints(username, dto.getPoints());
        return Response.buildSuccess(result);
    }
}
