package com.datainsight.parser.service;

import com.datainsight.parser.entity.ColumnMeta;
import com.datainsight.parser.entity.DataFile;
import com.datainsight.parser.entity.DataRecord;
import com.datainsight.parser.mapper.ColumnMetaMapper;
import com.datainsight.parser.mapper.DataFileMapper;
import com.datainsight.parser.mapper.DataRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelParserService {

    private final DataFileMapper dataFileMapper;
    private final ColumnMetaMapper columnMetaMapper;
    private final DataRecordMapper dataRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern INT_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+\\.\\d+$");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    @Transactional
    public void parse(Long fileId) {
        DataFile dataFile = dataFileMapper.selectById(fileId);
        if (dataFile == null) return;

        dataFile.setStatus("PARSING");
        dataFileMapper.updateById(dataFile);

        try (FileInputStream fis = new FileInputStream(dataFile.getFilePath());
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getLastRowNum();

            if (totalRows < 1) throw new IllegalArgumentException("Excel为空");

            // 读表头
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                headers.add(cell != null ? cell.toString().trim() : "col_" + i);
            }

            // 采样数据用于类型推断
            List<String[]> sampleRows = new ArrayList<>();
            int sampleSize = Math.min(totalRows, 200);
            for (int i = 1; i <= sampleSize; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String[] vals = new String[headers.size()];
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    vals[j] = cell != null ? cell.toString().trim() : "";
                }
                sampleRows.add(vals);
            }

            // 类型推断
            List<String> columnTypes = inferColumnTypes(headers, sampleRows);

            // 保存列元数据
            for (int i = 0; i < headers.size(); i++) {
                ColumnMeta meta = new ColumnMeta();
                meta.setFileId(fileId);
                meta.setColumnName(headers.get(i));
                meta.setColumnType(columnTypes.get(i));
                meta.setOrdinalPosition(i);
                meta.setNullableRatio(calcNullableRatio(sampleRows, i));
                meta.setSampleValues(toJson(getSampleValues(sampleRows, i, 5)));
                columnMetaMapper.insert(meta);
            }

            // 写入数据行
            List<DataRecord> batch = new ArrayList<>();
            for (int i = 1; i <= totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> map = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    String val = cell != null ? cell.toString().trim() : "";
                    map.put("col_" + j, val);
                }

                DataRecord record = new DataRecord();
                record.setFileId(fileId);
                record.setRowIndex(i - 1);
                record.setRowData(objectMapper.writeValueAsString(map));
                batch.add(record);

                if (batch.size() >= 500) {
                    for (DataRecord r : batch) dataRecordMapper.insert(r);
                    batch.clear();
                }
            }
            for (DataRecord r : batch) dataRecordMapper.insert(r);

            dataFile.setStatus("COMPLETED");
            dataFile.setRowCount(totalRows);
            dataFile.setColumnCount(headers.size());
            dataFileMapper.updateById(dataFile);

            log.info("Excel解析完成: fileId={}, rows={}, cols={}", fileId, totalRows, headers.size());

        } catch (Exception e) {
            log.error("Excel解析失败: fileId={}", fileId, e);
            dataFile.setStatus("FAILED");
            dataFile.setErrorMsg(e.getMessage());
            dataFileMapper.updateById(dataFile);
        }
    }

    private List<String> inferColumnTypes(List<String> headers, List<String[]> rows) {
        List<String> types = new ArrayList<>();
        for (int col = 0; col < headers.size(); col++) {
            int intHits = 0, decimalHits = 0, dateHits = 0, total = 0;
            for (String[] row : rows) {
                String val = row[col];
                if (val == null || val.isEmpty()) continue;
                total++;
                if (INT_PATTERN.matcher(val).matches()) intHits++;
                else if (DECIMAL_PATTERN.matcher(val).matches()) decimalHits++;
                else if (tryParseDate(val)) dateHits++;
            }
            if (total == 0) types.add("VARCHAR");
            else if ((double) intHits / total > 0.8) types.add("INT");
            else if ((double) (intHits + decimalHits) / total > 0.8) types.add("DECIMAL");
            else if ((double) dateHits / total > 0.8) types.add("DATE");
            else types.add("VARCHAR");
        }
        return types;
    }

    private boolean tryParseDate(String val) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { LocalDate.parse(val.length() > 10 ? val.substring(0, 10) : val, fmt); return true; }
            catch (DateTimeParseException ignored) {}
        }
        return false;
    }

    private BigDecimal calcNullableRatio(List<String[]> rows, int col) {
        long nulls = rows.stream().filter(r -> r[col] == null || r[col].isEmpty()).count();
        return BigDecimal.valueOf(rows.isEmpty() ? 0 : (double) nulls / rows.size());
    }

    private List<String> getSampleValues(List<String[]> rows, int col, int max) {
        Set<String> set = new LinkedHashSet<>();
        for (String[] r : rows) {
            if (r[col] != null && !r[col].isEmpty()) { set.add(r[col]); if (set.size() >= max) break; }
        }
        return new ArrayList<>(set);
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "[]"; }
    }
}
