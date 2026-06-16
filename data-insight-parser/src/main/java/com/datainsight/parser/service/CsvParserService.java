package com.datainsight.parser.service;

import com.datainsight.parser.entity.ColumnMeta;
import com.datainsight.parser.entity.DataFile;
import com.datainsight.parser.entity.DataRecord;
import com.datainsight.parser.mapper.ColumnMetaMapper;
import com.datainsight.parser.mapper.DataFileMapper;
import com.datainsight.parser.mapper.DataRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvParserService {

    private final DataFileMapper dataFileMapper;
    private final ColumnMetaMapper columnMetaMapper;
    private final DataRecordMapper dataRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern INT_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+\\.\\d+$");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );

    /**
     * 解析 CSV 文件
     */
    @Transactional
    public void parse(Long fileId) {
        DataFile dataFile = dataFileMapper.selectById(fileId);
        if (dataFile == null) {
            log.error("文件记录不存在: fileId={}", fileId);
            return;
        }

        // 更新状态为解析中
        dataFile.setStatus("PARSING");
        dataFileMapper.updateById(dataFile);

        try {
            List<String[]> allRows = readAllRows(dataFile.getFilePath());

            if (allRows.isEmpty()) {
                throw new IllegalArgumentException("文件为空");
            }

            String[] headers = allRows.get(0);
            List<String[]> dataRows = allRows.subList(1, allRows.size());

            // 1. 推断列类型（采样前1000行）
            List<String> columnTypes = inferColumnTypes(headers, dataRows);

            // 2. 保存列元数据
            for (int i = 0; i < headers.length; i++) {
                ColumnMeta meta = new ColumnMeta();
                meta.setFileId(fileId);
                meta.setColumnName(headers[i]);
                meta.setColumnType(columnTypes.get(i));
                meta.setOrdinalPosition(i);
                meta.setNullableRatio(calcNullableRatio(dataRows, i));
                meta.setSampleValues(toJson(getSampleValues(dataRows, i, 5)));
                columnMetaMapper.insert(meta);
            }

            // 3. 批量写入数据行
            List<DataRecord> records = new ArrayList<>();
            for (int i = 0; i < dataRows.size(); i++) {
                DataRecord record = new DataRecord();
                record.setFileId(fileId);
                record.setRowIndex(i);
                record.setRowData(rowToJson(headers, dataRows.get(i)));
                records.add(record);

                // 每500行批量插入一次
                if (records.size() >= 500) {
                    for (DataRecord r : records) {
                        dataRecordMapper.insert(r);
                    }
                    records.clear();
                }
            }
            // 剩余行
            for (DataRecord r : records) {
                dataRecordMapper.insert(r);
            }

            // 4. 更新文件状态
            dataFile.setStatus("COMPLETED");
            dataFile.setRowCount(dataRows.size());
            dataFile.setColumnCount(headers.length);
            dataFileMapper.updateById(dataFile);

            log.info("文件解析完成: fileId={}, rows={}, columns={}", fileId, dataRows.size(), headers.length);

        } catch (Exception e) {
            log.error("文件解析失败: fileId={}", fileId, e);
            dataFile.setStatus("FAILED");
            dataFile.setErrorMsg(e.getMessage());
            dataFileMapper.updateById(dataFile);
        }
    }

    /** 读取所有行 */
    private List<String[]> readAllRows(String filePath) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            rows.addAll(reader.readAll());
        }
        return rows;
    }

    /** 推断每列的数据类型 */
    private List<String> inferColumnTypes(String[] headers, List<String[]> dataRows) {
        List<String> types = new ArrayList<>();
        int sampleSize = Math.min(dataRows.size(), 200);

        for (int col = 0; col < headers.length; col++) {
            String type = inferSingleColumnType(dataRows, col, sampleSize);
            types.add(type);
        }
        return types;
    }

    /** 推断单列类型（简单投票法） */
    private String inferSingleColumnType(List<String[]> rows, int col, int sampleSize) {
        int intHits = 0, decimalHits = 0, dateHits = 0, total = 0;

        for (int i = 0; i < sampleSize; i++) {
            String val = rows.get(i)[col];
            if (val == null || val.trim().isEmpty()) continue;
            val = val.trim();
            total++;

            if (INT_PATTERN.matcher(val).matches()) intHits++;
            else if (DECIMAL_PATTERN.matcher(val).matches()) decimalHits++;
            else if (tryParseDate(val)) dateHits++;
        }

        double ratio = total > 0 ? (double) intHits / total : 0;
        if (ratio > 0.8 || intHits == total) return "INT";
        if ((double) (intHits + decimalHits) / total > 0.8) return "DECIMAL";
        if ((double) dateHits / total > 0.8) return "DATE";
        return "VARCHAR";
    }

    private boolean tryParseDate(String val) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                LocalDate.parse(val, fmt);
                return true;
            } catch (DateTimeParseException ignored) {
            }
        }
        return false;
    }

    private BigDecimal calcNullableRatio(List<String[]> rows, int col) {
        long nullCount = 0;
        for (String[] row : rows) {
            String val = row[col];
            if (val == null || val.trim().isEmpty()) nullCount++;
        }
        return BigDecimal.valueOf((double) nullCount / rows.size());
    }

    private List<String> getSampleValues(List<String[]> rows, int col, int max) {
        Set<String> samples = new LinkedHashSet<>();
        for (String[] row : rows) {
            String val = row[col];
            if (val != null && !val.trim().isEmpty()) {
                samples.add(val.trim());
                if (samples.size() >= max) break;
            }
        }
        return new ArrayList<>(samples);
    }

    private String rowToJson(String[] headers, String[] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String val = i < values.length ? values[i] : "";
            // 使用 col_N 作为键（MySQL JSON路径不支持中文）
            map.put("col_" + i, val != null ? val.trim() : "");
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
