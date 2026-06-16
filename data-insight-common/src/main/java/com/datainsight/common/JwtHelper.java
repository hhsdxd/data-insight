package com.datainsight.common;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

public class JwtHelper {

    // TODO: 迁移到 Nacos 配置中心
    public static final String SECRET = "data-insight-jwt-secret-key-2026";
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET);

    public static String createToken(Long userId, String username) {
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withExpiresAt(new Date(System.currentTimeMillis() + 7 * 24 * 3600 * 1000L))
                .sign(ALGORITHM);
    }

    public static boolean verify(String token) {
        try {
            JWTVerifier verifier = JWT.require(ALGORITHM).build();
            verifier.verify(token);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    public static DecodedJWT parse(String token) {
        return JWT.require(ALGORITHM).build().verify(token);
    }

    public static Long getUserId(String token) {
        return parse(token).getClaim("userId").asLong();
    }

    public static String getUsername(String token) {
        return parse(token).getClaim("username").asString();
    }
}
