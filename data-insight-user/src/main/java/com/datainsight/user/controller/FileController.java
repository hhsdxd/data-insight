package com.datainsight.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datainsight.common.BizException;
import com.datainsight.common.JwtHelper;
import com.datainsight.common.R;
import com.datainsight.user.entity.UserFile;
import com.datainsight.user.mapper.UserFileMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/user/file")
@RequiredArgsConstructor
public class FileController {

    private final UserFileMapper userFileMapper;

    @Value("${data-insight.upload-dir:./uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public R<UserFile> upload(@RequestParam("file") MultipartFile file,
                              HttpServletRequest request) throws IOException {
        Long userId = getUserId(request);

        // 创建上传目录
        Path uploadPath = Paths.get(uploadDir, userId.toString());
        Files.createDirectories(uploadPath);

        // 保存文件
        String storedName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(storedName);
        file.transferTo(filePath.toFile());

        // 记录到数据库
        UserFile userFile = new UserFile();
        userFile.setUserId(userId);
        userFile.setOriginalName(file.getOriginalFilename());
        userFile.setFileSize(file.getSize());
        userFile.setFilePath(filePath.toString());
        userFile.setStatus("PENDING");
        userFileMapper.insert(userFile);

        log.info("文件上传成功: userId={}, file={}, size={}", userId, file.getOriginalFilename(), file.getSize());

        // TODO: 发送 RocketMQ 消息通知解析服务

        return R.ok(userFile);
    }

    @GetMapping("/list")
    public R<List<UserFile>> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<UserFile> files = userFileMapper.selectList(
                new LambdaQueryWrapper<UserFile>()
                        .eq(UserFile::getUserId, userId)
                        .orderByDesc(UserFile::getCreateTime));
        return R.ok(files);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        UserFile file = userFileMapper.selectById(id);
        if (file == null || !file.getUserId().equals(userId)) {
            throw new BizException("文件不存在");
        }
        userFileMapper.deleteById(id);
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
