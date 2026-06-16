package com.datainsight.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datainsight.common.BizException;
import com.datainsight.common.JwtHelper;
import com.datainsight.common.R;
import com.datainsight.user.entity.User;
import com.datainsight.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;

    @PostMapping("/register")
    public R<Map<String, String>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String email = body.get("email");

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(DigestUtils.md5DigestAsHex(password.getBytes()));
        user.setEmail(email);
        userMapper.insert(user);

        String token = JwtHelper.createToken(user.getId(), user.getUsername());
        return R.ok(Map.of("token", token, "userId", user.getId().toString()));
    }

    @PostMapping("/login")
    public R<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String md5Pwd = DigestUtils.md5DigestAsHex(password.getBytes());

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .eq(User::getPassword, md5Pwd));
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }

        String token = JwtHelper.createToken(user.getId(), user.getUsername());
        return R.ok(Map.of("token", token, "userId", user.getId().toString()));
    }

    @GetMapping("/info")
    public R<User> info(HttpServletRequest request) {
        Long userId = getUserId(request);

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setPassword(null);
        return R.ok(user);
    }

    /** 优先从 Gateway 传递的 Header 获取，兼容直连模式从 Authorization 解析 */
    private Long getUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null) {
            return Long.valueOf(userIdHeader);
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return JwtHelper.getUserId(authHeader.replace("Bearer ", ""));
        }
        throw new BizException(401, "未登录");
    }
}
