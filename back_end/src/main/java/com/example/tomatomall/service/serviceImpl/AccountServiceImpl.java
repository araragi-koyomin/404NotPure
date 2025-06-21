package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.AccountUpdateDTO;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.AccountService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.AccountSimpleVO;
import com.example.tomatomall.vo.AccountVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 账户服务实现类
 * 提供账户注册、登录、信息查询与修改等功能
 */
@Service
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final TokenUtil tokenUtil;
    private final PasswordEncoder passwordEncoder;

    public AccountServiceImpl(UserRepository userRepository,
                              TokenUtil tokenUtil,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenUtil = tokenUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册新账户
     * @param account 账户实体
     * @return 注册结果
     * @throws TomatoException 用户名或手机号已存在时抛出
     */
    @Override
    @Transactional
    public String register(Account account) {
        validateAccountUniqueness(account);
        account.setPassword(passwordEncoder.encode(account.getPassword()));
        account.setPoints(0);
        userRepository.save(account);
        return "注册成功";
    }

    /**
     * 账户登录
     * @param accountVO 账户视图对象
     * @return 认证令牌
     * @throws TomatoException 登录失败时抛出
     */
    @Override
    public String login(AccountVO accountVO) {
        Account tmp = accountVO.toPO();
        String usernameOrTelephone = tmp.getUsername();
        String rawPassword = tmp.getPassword();

        Account account = findAccountByIdentifier(usernameOrTelephone)
            .orElseThrow(TomatoException::loginFailure);

        if (!passwordEncoder.matches(rawPassword, account.getPassword())) {
            throw TomatoException.loginFailure();
        }

        return tokenUtil.generateToken(account.getId());
    }

    /**
     * 获取账户简略信息
     * @param identifier 用户名或手机号
     * @return 账户简略视图对象
     * @throws TomatoException 账户不存在时抛出
     */
    @Override
    public AccountSimpleVO getAccount(String identifier) {
        return findAccountByIdentifier(identifier)
            .map(Account::toSimpleVO)
            .orElseThrow(TomatoException::userNotExist);
    }

    /**
     * 更新账户信息
     * @param accountUpdateDTO 账户更新数据传输对象
     * @return 更新结果
     * @throws TomatoException 账户不存在或手机号已存在时抛出
     */
    @Override
    @Transactional
    public String update(AccountUpdateDTO accountUpdateDTO) {
        Account account = userRepository.findByUsername(accountUpdateDTO.getUsername())
            .orElseThrow(TomatoException::userNotExist);

        updateAccountFields(account, accountUpdateDTO);
        userRepository.save(account);
        return "用户信息已更新";
    }

    /**
     * 获取账户积分
     * @param identifier 用户名或手机号
     * @return 积分数量
     * @throws TomatoException 账户不存在时抛出
     */
    @Override
    public Integer getUserPoints(String identifier) {
        return findAccountByIdentifier(identifier)
            .map(Account::getPoints)
            .orElseThrow(TomatoException::userNotExist);
    }
  
    @Override
    public Account getAccountById(int id){
        Optional<Account> account = userRepository.findById(id);
        if (!account.isPresent()) {
            throw TomatoException.userNotExist();
        }
        return account.get();
    }

    /**
     * 更新账户积分
     * @param identifier 用户名或手机号
     * @param points 新积分值
     * @return 更新结果
     * @throws TomatoException 账户不存在或积分无效时抛出
     */
    @Override
    @Transactional
    public String updateUserPoints(String identifier, int points) {
        if (points < 0) {
            throw TomatoException.pointsInvalid();
        }

        Account account = findAccountByIdentifier(identifier)
            .orElseThrow(TomatoException::userNotExist);

        account.setPoints(points);
        userRepository.save(account);
        return "修改成功";
    }

    // 私有辅助方法

    private void validateAccountUniqueness(Account account) {
        if (userRepository.findByUsername(account.getUsername()).isPresent()) {
            throw TomatoException.userNameExist();
        }
        if (userRepository.findByTelephone(account.getTelephone()).isPresent()) {
            throw TomatoException.telephoneExist();
        }
    }

    private Optional<Account> findAccountByIdentifier(String identifier) {
        Optional<Account> account = userRepository.findByUsername(identifier);
        return account.isPresent() ? account : userRepository.findByTelephone(identifier);
    }

    private void updateAccountFields(Account account, AccountUpdateDTO dto) {
        if (dto.getPassword() != null) {
            account.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        if (dto.getName() != null) account.setName(dto.getName());
        if (dto.getAvatar() != null) account.setAvatar(dto.getAvatar());
        if (dto.getTelephone() != null) {
            if (userRepository.findByTelephone(dto.getTelephone())
                .filter(a -> !a.getId().equals(account.getId()))
                .isPresent()) {
                throw TomatoException.telephoneExist();
            }
            account.setTelephone(dto.getTelephone());
        }
        if (dto.getEmail() != null) account.setEmail(dto.getEmail());
        if (dto.getLocation() != null) account.setLocation(dto.getLocation());
        if (dto.getRole() != null) account.setRole(dto.getRole());
    }
}
