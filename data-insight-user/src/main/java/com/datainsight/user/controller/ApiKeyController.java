package com.datainsight.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datainsight.common.BizException;
import com.datainsight.common.JwtHelper;
import com.datainsight.common.R;
import com.datainsight.user.entity.ApiKey;
import com.datainsight.user.mapper.ApiKeyMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user/key")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyMapper apiKeyMapper;

    @PostMapping("/generate")
    public R<ApiKey> generate(HttpServletRequest request) {
        Long userId = getUserId(request);

        // 限制每个用户最多 5 个 key
        Long count = apiKeyMapper.selectCount(
                new LambdaQueryWrapper<ApiKey>()
                        .eq(ApiKey::getUserId, userId)
                        .eq(ApiKey::getStatus, "ACTIVE"));
        if (count >= 5) {
            throw new BizException("最多生成5个API Key");
        }

        ApiKey key = new ApiKey();
        key.setUserId(userId);
        key.setAccessKey("ak-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        key.setSecretKey("sk-" + UUID.randomUUID().toString().replace("-", ""));
        key.setStatus("ACTIVE");
        apiKeyMapper.insert(key);

        return R.ok(key);
    }

    @GetMapping("/list")
    public R<List<ApiKey>> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<ApiKey> keys = apiKeyMapper.selectList(
                new LambdaQueryWrapper<ApiKey>()
                        .eq(ApiKey::getUserId, userId)
                        .orderByDesc(ApiKey::getCreateTime));
        // 不返回 secretKey
        keys.forEach(k -> k.setSecretKey(null));
        return R.ok(keys);
    }

    @PostMapping("/revoke/{id}")
    public R<Void> revoke(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null || !key.getUserId().equals(userId)) {
            throw new BizException("Key不存在");
        }
        key.setStatus("REVOKED");
        apiKeyMapper.updateById(key);
        return R.ok();
    }

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
