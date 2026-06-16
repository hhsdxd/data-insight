package com.datainsight.parser.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datainsight.common.BizException;
import com.datainsight.common.R;
import com.datainsight.parser.entity.ColumnMeta;
import com.datainsight.parser.entity.DataFile;
import com.datainsight.parser.entity.DataRecord;
import com.datainsight.parser.mapper.ColumnMetaMapper;
import com.datainsight.parser.mapper.DataFileMapper;
import com.datainsight.parser.mapper.DataRecordMapper;
import com.datainsight.parser.service.CsvParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class ParserController {

    private final DataFileMapper dataFileMapper;
    private final ColumnMetaMapper columnMetaMapper;
    private final DataRecordMapper dataRecordMapper;
    private final CsvParserService csvParserService;

    /** 文件解析状态 */
    @GetMapping("/status/{fileId}")
    public R<DataFile> status(@PathVariable Long fileId) {
        DataFile file = dataFileMapper.selectById(fileId);
        if (file == null) {
            throw new BizException("文件不存在");
        }
        return R.ok(file);
    }

    /** 触发解析（同步或异步） */
    @PostMapping("/parse/{fileId}")
    public R<String> parse(@PathVariable Long fileId) {
        DataFile file = dataFileMapper.selectById(fileId);
        if (file == null) {
            throw new BizException("文件不存在");
        }
        csvParserService.parse(fileId);
        return R.ok("解析完成");
    }

    /** 数据预览（前100行） */
    @GetMapping("/preview/{fileId}")
    public R<Map<String, Object>> preview(@PathVariable Long fileId) {
        DataFile file = dataFileMapper.selectById(fileId);
        if (file == null) {
            throw new BizException("文件不存在");
        }

        List<ColumnMeta> columns = columnMetaMapper.selectList(
                new LambdaQueryWrapper<ColumnMeta>()
                        .eq(ColumnMeta::getFileId, fileId)
                        .orderByAsc(ColumnMeta::getOrdinalPosition));

        List<DataRecord> records = dataRecordMapper.selectPreview(fileId, 100);

        return R.ok(Map.of(
                "file", file,
                "columns", columns,
                "records", records,
                "totalRows", dataRecordMapper.countByFileId(fileId)
        ));
    }

    /** 列统计信息 */
    @GetMapping("/stats/{fileId}")
    public R<Map<String, Object>> stats(@PathVariable Long fileId) {
        DataFile file = dataFileMapper.selectById(fileId);
        if (file == null) {
            throw new BizException("文件不存在");
        }

        List<ColumnMeta> columns = columnMetaMapper.selectList(
                new LambdaQueryWrapper<ColumnMeta>()
                        .eq(ColumnMeta::getFileId, fileId)
                        .orderByAsc(ColumnMeta::getOrdinalPosition));

        return R.ok(Map.of(
                "file", file,
                "columns", columns
        ));
    }

    /** 数据清洗：去重 + 填空值 */
    @PostMapping("/clean/{fileId}")
    public R<Map<String, Object>> clean(@PathVariable Long fileId) {
        DataFile file = dataFileMapper.selectById(fileId);
        if (file == null) throw new BizException("文件不存在");

        List<DataRecord> records = dataRecordMapper.selectList(
                new LambdaQueryWrapper<DataRecord>().eq(DataRecord::getFileId, fileId));

        // 去重（按 rowData 内容）
        Set<String> seen = new HashSet<>();
        List<DataRecord> unique = new ArrayList<>();
        for (DataRecord r : records) {
            if (seen.add(r.getRowData())) unique.add(r);
        }
        int removed = records.size() - unique.size();

        // 删除所有旧记录，重新插入去重后的
        dataRecordMapper.delete(new LambdaQueryWrapper<DataRecord>().eq(DataRecord::getFileId, fileId));
        for (DataRecord r : unique) dataRecordMapper.insert(r);

        file.setRowCount(unique.size());
        dataFileMapper.updateById(file);

        return R.ok(Map.of("duplicatesRemoved", removed, "newRowCount", unique.size()));
    }
}
