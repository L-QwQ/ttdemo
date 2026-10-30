package com.ttdemo.modules.lottery.sync;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 中国福利彩票官方开奖查询（双色球 / 3D / 快乐8）。
 * 数据来源：www.cwl.gov.cn 公开接口。
 */
@Component
public class CwlDrawClient {

    private static final String BASE =
            "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice";
    private static final String REFERER = "https://www.cwl.gov.cn/";
    private static final int MAX_PAGE = 10;

    private final DrawHttpClient http;
    private final ObjectMapper objectMapper;

    public CwlDrawClient(DrawHttpClient http, ObjectMapper objectMapper) {
        this.http = http;
        this.objectMapper = objectMapper;
    }

    /**
     * @param gameName ssq / 3d / kl8
     */
    public List<DrawRecord> fetch(String gameName, int count) throws Exception {
        List<DrawRecord> all = new ArrayList<>();
        int pageSize = Math.min(Math.max(count, 1), 100);
        int pageNo = 1;
        while (all.size() < count && pageNo <= MAX_PAGE) {
            String url = BASE + "?name=" + gameName
                    + "&issueCount=" + pageSize
                    + "&pageNo=" + pageNo
                    + "&pageSize=" + pageSize
                    + "&systemType=PC";
            String body = http.get(url, REFERER);
            JsonNode root = objectMapper.readTree(body);
            JsonNode result = root.get("result");
            if (result == null || !result.isArray() || result.isEmpty()) {
                break;
            }
            for (JsonNode node : result) {
                DrawRecord record = toRecord(node);
                if (record != null) {
                    all.add(record);
                }
            }
            if (result.size() < pageSize) {
                break;
            }
            pageNo++;
        }
        return all.size() > count ? all.subList(0, count) : all;
    }

    private DrawRecord toRecord(JsonNode node) {
        String code = node.path("code").asString("");
        String date = node.path("date").asString("");
        String red = node.path("red").asString("");
        String blue = node.path("blue").asString("");
        if (code.isBlank() || date.length() < 10) {
            return null;
        }
        LocalDate drawDate;
        try {
            drawDate = LocalDate.parse(date.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
        return new DrawRecord(code, drawDate, normalize(red), normalize(blue), node.toString());
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", ",").replaceAll(",{2,}", ",");
    }
}
