package com.datainsight.user.controller;

import com.datainsight.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("user-service is running");
    }
}
