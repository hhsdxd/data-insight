package com.datainsight.viz.feign;

import com.datainsight.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "data-insight-parser")
public interface ParserFeignClient {

    @GetMapping("/api/data/preview/{fileId}")
    R<Map<String, Object>> preview(@PathVariable Long fileId);

    @GetMapping("/api/data/stats/{fileId}")
    R<Map<String, Object>> stats(@PathVariable Long fileId);
}
