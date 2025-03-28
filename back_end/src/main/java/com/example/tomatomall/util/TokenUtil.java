package com.example.tomatomall.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class TokenUtil {
    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000;
    private static final String SECRET = "404NotPure";

    @Autowired
    UserRepository userRepository;

    public boolean verifyToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            JWTVerifier verifier = JWT.require(algorithm).build();
            verifier.verify(token); // 校验成功说明 token 有效
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String generateToken(Integer userId) {
        Date expireDate = new Date(System.currentTimeMillis() + EXPIRE_TIME);
        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        return JWT.create()
                .withSubject(String.valueOf(userId)) // 将 id 转为字符串作为 subject
                .withExpiresAt(expireDate)
                .sign(algorithm);
    }

    public static Integer getUserIdFromToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT decodedJWT = verifier.verify(token);
            return Integer.parseInt(decodedJWT.getSubject()); // 解析出的是 id
        } catch (Exception e) {
            throw TomatoException.notLogin(); // 或自定义异常
        }
    }
}
