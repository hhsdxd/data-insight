package com.datainsight.parser.controller;

import com.datainsight.common.BizException;
import com.datainsight.common.R;
import com.datainsight.parser.entity.DataFile;
import com.datainsight.parser.mapper.DataFileMapper;
import com.datainsight.parser.service.CsvParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;

/**
 * 解析触发接口 —— 接收文件元数据，创建 DataFile 记录并触发解析。
 */
@Slf4j
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class ParseTriggerController {

    private final DataFileMapper dataFileMapper;
    private final CsvParserService csvParserService;
    private final com.datainsight.parser.service.ExcelParserService excelParserService;

    @PostMapping("/trigger-parse")
    public R<DataFile> triggerParse(@RequestBody Map<String, Object> body) {
        String filePath = (String) body.get("filePath");
        String originalName = (String) body.get("originalName");
        Long fileSize = body.get("fileSize") != null ? Long.valueOf(body.get("fileSize").toString()) : 0L;
        Long userId = body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : 1L;

        File f = new File(filePath);
        if (!f.exists()) throw new BizException("文件不存在: " + filePath);

        DataFile dataFile = new DataFile();
        dataFile.setUserId(userId);
        dataFile.setOriginalName(originalName);
        dataFile.setFilePath(filePath);
        dataFile.setFileSize(fileSize);
        dataFile.setStatus("PENDING");
        dataFileMapper.insert(dataFile);

        log.info("创建解析任务: fileId={}, name={}", dataFile.getId(), originalName);

        // 根据扩展名选择解析器
        String name = originalName.toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            excelParserService.parse(dataFile.getId());
        } else {
            csvParserService.parse(dataFile.getId());
        }

        dataFile = dataFileMapper.selectById(dataFile.getId());
        return R.ok(dataFile);
    }
}
