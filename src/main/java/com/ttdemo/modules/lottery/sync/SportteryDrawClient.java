package com.ttdemo.modules.lottery.sync;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 中国体育彩票官方开奖查询（超级大乐透 / 排列3）。
 * 数据来源：webapi.sporttery.cn 公开接口。
 */
@Component
public class SportteryDrawClient {

    private static final String BASE =
            "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry";
    private static final String REFERER = "https://static.sporttery.cn/";
    private static final int MAX_PAGE = 10;
    /** 大乐透：5 个前区 + 2 个后区 */
    private static final int GAME_DLT = 85;

    private final DrawHttpClient http;
    private final ObjectMapper objectMapper;

    public SportteryDrawClient(DrawHttpClient http, ObjectMapper objectMapper) {
        this.http = http;
        this.objectMapper = objectMapper;
    }

    /**
     * @param gameNo 85 = 超级大乐透，35 = 排列3
     */
    public List<DrawRecord> fetch(int gameNo, int count) throws Exception {
        List<DrawRecord> all = new ArrayList<>();
        int pageSize = Math.min(Math.max(count, 1), 100);
        int pageNo = 1;
        while (all.size() < count && pageNo <= MAX_PAGE) {
            String url = BASE + "?gameNo=" + gameNo
                    + "&provinceId=0"
                    + "&pageSize=" + pageSize
                    + "&isVerify=1"
                    + "&pageNo=" + pageNo;
            String body = http.get(url, REFERER);
            JsonNode root = objectMapper.readTree(body);
            JsonNode list = root.path("value").path("list");
            if (list == null || !list.isArray() || list.isEmpty()) {
                break;
            }
            for (JsonNode node : list) {
                DrawRecord record = toRecord(node, gameNo);
                if (record != null) {
                    all.add(record);
                }
            }
            if (list.size() < pageSize) {
                break;
            }
            pageNo++;
        }
        return all.size() > count ? all.subList(0, count) : all;
    }

    private DrawRecord toRecord(JsonNode node, int gameNo) {
        String num = node.path("lotteryDrawNum").asString("");
        String result = node.path("lotteryDrawResult").asString("");
        String time = node.path("lotteryDrawTime").asString("");
        if (num.isBlank() || result.isBlank() || time.isBlank()) {
            return null;
        }
        LocalDate drawDate;
        try {
            drawDate = LocalDate.parse(time.length() > 10 ? time.substring(0, 10) : time);
        } catch (Exception e) {
            return null;
        }
        List<String> numbers = Arrays.stream(result.trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        String red;
        String blue = "";
        if (gameNo == GAME_DLT && numbers.size() >= 7) {
            red = String.join(",", numbers.subList(0, 5));
            blue = String.join(",", numbers.subList(5, 7));
        } else {
            red = String.join(",", numbers);
        }
        return new DrawRecord(num, drawDate, red, blue, node.toString());
    }
}
