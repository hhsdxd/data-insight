package com.datainsight.viz.controller;

import com.datainsight.common.BizException;
import com.datainsight.common.R;
import com.datainsight.viz.feign.ParserFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 可视化服务：图表数据、报告生成
 */
@Slf4j
@RestController
@RequestMapping("/api/viz")
@RequiredArgsConstructor
public class VizController {

    private final ParserFeignClient parserFeignClient;

    /** 生成柱状图配置（默认取第一列做X轴，最后一列数值列做Y轴） */
    @PostMapping("/chart/bar/{fileId}")
    public R<Map<String, Object>> barChart(@PathVariable Long fileId,
                                           @RequestBody Map<String, String> params) {
        Map<String, Object> data = fetchData(fileId);
        return R.ok(buildBarOption(data, params));
    }

    /** 生成饼图配置 */
    @PostMapping("/chart/pie/{fileId}")
    public R<Map<String, Object>> pieChart(@PathVariable Long fileId,
                                           @RequestBody Map<String, String> params) {
        Map<String, Object> data = fetchData(fileId);
        return R.ok(buildPieOption(data, params));
    }

    /** 生成折线图配置 */
    @PostMapping("/chart/line/{fileId}")
    public R<Map<String, Object>> lineChart(@PathVariable Long fileId,
                                            @RequestBody Map<String, String> params) {
        Map<String, Object> data = fetchData(fileId);
        return R.ok(buildLineOption(data, params));
    }

    /** 汇总分析报告 */
    @GetMapping("/report/{fileId}")
    public R<Map<String, Object>> report(@PathVariable Long fileId) {
        R<Map<String, Object>> statsResult = parserFeignClient.stats(fileId);
        if (statsResult.getCode() != 200) {
            throw new BizException("获取文件统计信息失败");
        }
        Map<String, Object> fileData = statsResult.getData();
        Map<String, Object> file = (Map<String, Object>) fileData.get("file");
        List<Map<String, Object>> columns = (List<Map<String, Object>>) fileData.get("columns");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("file", file);
        report.put("columnCount", columns.size());
        report.put("columnTypes", columns.stream()
                .collect(Collectors.groupingBy(
                        c -> (String) c.get("columnType"),
                        Collectors.counting())));
        report.put("recommendations", generateRecommendations(columns));
        return R.ok(report);
    }

    /** 获取可用图表模板列表 */
    @GetMapping("/templates")
    public R<List<Map<String, String>>> templates() {
        List<Map<String, String>> templates = List.of(
                Map.of("type", "bar", "name", "柱状图", "description", "适合分类对比"),
                Map.of("type", "pie", "name", "饼图", "description", "适合占比分析"),
                Map.of("type", "line", "name", "折线图", "description", "适合趋势变化"),
                Map.of("type", "scatter", "name", "散点图", "description", "适合相关性分析")
        );
        return R.ok(templates);
    }

    private Map<String, Object> fetchData(Long fileId) {
        R<Map<String, Object>> result = parserFeignClient.preview(fileId);
        if (result.getCode() != 200) {
            throw new BizException("获取数据失败");
        }
        return result.getData();
    }

    private Map<String, Object> buildBarOption(Map<String, Object> data, Map<String, String> params) {
        List<Map<String, Object>> columns = (List<Map<String, Object>>) data.get("columns");
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");

        String xCol = params.getOrDefault("xCol", (String) ((Map) columns.get(0)).get("columnName"));
        String yCol = params.getOrDefault("yCol",
                (String) ((Map) columns.get(columns.size() - 1)).get("columnName"));

        List<String> xData = new ArrayList<>();
        List<Object> yData = new ArrayList<>();

        for (Map<String, Object> record : records) {
            try {
                String rowJson = (String) record.get("rowData");
                Map<String, Object> row = new com.fasterxml.jackson.databind.ObjectMapper().readValue(rowJson, Map.class);
                xData.add(String.valueOf(row.getOrDefault(xCol, "")));
                yData.add(row.getOrDefault(yCol, ""));
            } catch (Exception ignored) {}
        }

        return Map.of(
                "title", Map.of("text", yCol + " 柱状图"),
                "tooltip", Map.of(),
                "xAxis", Map.of("data", xData),
                "yAxis", Map.of("type", "value"),
                "series", List.of(Map.of("type", "bar", "data", yData))
        );
    }

    private Map<String, Object> buildPieOption(Map<String, Object> data, Map<String, String> params) {
        List<Map<String, Object>> columns = (List<Map<String, Object>>) data.get("columns");
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");

        String nameCol = params.getOrDefault("nameCol", (String) ((Map) columns.get(0)).get("columnName"));
        String valueCol = params.getOrDefault("valueCol",
                (String) ((Map) columns.get(columns.size() - 1)).get("columnName"));

        List<Map<String, Object>> pieData = new ArrayList<>();

        for (Map<String, Object> record : records) {
            try {
                String rowJson = (String) record.get("rowData");
                Map<String, Object> row = new com.fasterxml.jackson.databind.ObjectMapper().readValue(rowJson, Map.class);
                pieData.add(Map.of(
                        "name", row.getOrDefault(nameCol, ""),
                        "value", row.getOrDefault(valueCol, "")
                ));
            } catch (Exception ignored) {}
        }

        return Map.of(
                "title", Map.of("text", valueCol + " 占比"),
                "tooltip", Map.of(),
                "series", List.of(Map.of("type", "pie", "data", pieData))
        );
    }

    private Map<String, Object> buildLineOption(Map<String, Object> data, Map<String, String> params) {
        // 折线图复用柱状图的数据结构，只改 type
        Map<String, Object> option = buildBarOption(data, params);
        ((List<Map>) option.get("series")).get(0).put("type", "line");
        ((Map) option.get("title")).put("text", ((Map) option.get("title")).get("text").toString().replace("柱状图", "折线图"));
        return option;
    }

    private String generateRecommendations(List<Map<String, Object>> columns) {
        StringBuilder sb = new StringBuilder();
        long numericCount = columns.stream()
                .filter(c -> "INT".equals(c.get("columnType")) || "DECIMAL".equals(c.get("columnType")))
                .count();
        long varcharCount = columns.stream()
                .filter(c -> "VARCHAR".equals(c.get("columnType")))
                .count();
        long dateCount = columns.stream()
                .filter(c -> "DATE".equals(c.get("columnType")))
                .count();

        if (numericCount > 0) {
            sb.append("数据包含 ").append(numericCount).append(" 个数值列，适合柱状图或折线图。");
        }
        if (varcharCount > 0 && numericCount > 0) {
            sb.append("分类列 + 数值列的组合适合饼图分析。");
        }
        if (dateCount > 0 && numericCount > 0) {
            sb.append("日期列适合趋势折线图分析。");
        }
        return sb.toString();
    }
}
