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

    @Autowired
    private TokenUtil tokenUtil;

    /**
     * 根据用户名获取用户信息
     * @param username 用户名
     * @return 用户详细信息
     */
    @GetMapping("/{username}")
    public Response<AccountSimpleVO> getAccountByUsername(
            @PathVariable String username,
            HttpServletRequest request
    ) {
        requireOwnAccount(username, request);
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
        requireOwnAccount(dto.getUsername(), request);
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
        tokenUtil.setTokenToCookie(response, token);
        return Response.buildSuccess(token);
    }

    @PostMapping("/logout")
    public Response<String> logout(HttpServletResponse response) {
        tokenUtil.clearTokenCookie(response);
        return Response.buildSuccess("退出登录成功");
    }

    /**
     * 获取账户积分
     * @param username 用户名
     * @return 积分数量
     */
    @GetMapping("/{username}/points")
    public Response<Integer> getAccountPoints(@PathVariable String username, HttpServletRequest request) {
        requireOwnAccount(username, request);
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
        // 积分的增加和扣减必须由明确的商城业务触发，不能开放通用 HTTP 修改入口。
        throw TomatoException.noPermission();
    }

    private Account requireOwnAccount(String username, HttpServletRequest request) {
        int accountId = tokenUtil.getUserIdFromRequest(request);
        Account currentAccount = userRepository.findById(accountId)
                .orElseThrow(TomatoException::notLogin);
        boolean matchesUsername = username.equals(currentAccount.getUsername());
        boolean matchesTelephone = username.equals(currentAccount.getTelephone());
        if (!matchesUsername && !matchesTelephone) {
            throw TomatoException.noPermission();
        }
        return currentAccount;
    }
}
