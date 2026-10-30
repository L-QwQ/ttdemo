package com.ttdemo.modules.random;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 玄学选号引擎：用卦象、干支、五行、牌阵等传统术数的「数」给号码池加权。
 * <p>
 * 设计原则：
 * 1. 只影响权重，不直接决定结果，最终仍由随机抽样落号；
 * 2. 输入全部来自公历日期与随机掷卦，可复现、可解释，不做任何结果承诺；
 * 3. 干支算法采用通用儒略日推算（日柱精确，年柱按公历年近似）。
 */
public final class MysticEngine {

    private MysticEngine() {
    }

    /** 占卜结果：权重表 + 解说文案 */
    public record Oracle(String title, String narrative, Map<Integer, Double> weights) {
    }

    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] ZODIAC = {"鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"};

    /** 天干五行（0-9 对应甲乙丙丁戊己庚辛壬癸） */
    private static final String[] GAN_ELEMENT = {"木", "木", "火", "火", "土", "土", "金", "金", "水", "水"};
    /** 地支五行（0-11 对应子丑寅卯辰巳午未申酉戌亥） */
    private static final String[] ZHI_ELEMENT = {"水", "土", "木", "木", "土", "火", "火", "土", "金", "金", "土", "水"};

    /** 河图数：五行 → 尾数集合 */
    private static final Map<String, int[]> HETU = Map.of(
            "水", new int[]{1, 6},
            "火", new int[]{2, 7},
            "木", new int[]{3, 8},
            "金", new int[]{4, 9},
            "土", new int[]{5, 0});

    /** 相生：key 生 value */
    private static final Map<String, String> SHENG = Map.of(
            "木", "火", "火", "土", "土", "金", "金", "水", "水", "木");

    /** 三爻（初、中、上）二进制 → 八卦索引；0 坤 1 震 2 坎 3 兑 4 艮 5 离 6 巽 7 乾 */
    private static final String[] TRIGRAM = {"坤", "震", "坎", "兑", "艮", "离", "巽", "乾"};
    /** 八卦先天数：乾1 兑2 离3 震4 巽5 坎6 艮7 坤8 */
    private static final Map<String, Integer> TRIGRAM_NO = Map.ofEntries(
            Map.entry("乾", 1), Map.entry("兑", 2), Map.entry("离", 3), Map.entry("震", 4),
            Map.entry("巽", 5), Map.entry("坎", 6), Map.entry("艮", 7), Map.entry("坤", 8));
    private static final Map<String, String> TRIGRAM_ELEMENT = Map.ofEntries(
            Map.entry("乾", "金"), Map.entry("兑", "金"), Map.entry("离", "火"), Map.entry("震", "木"),
            Map.entry("巽", "木"), Map.entry("坎", "水"), Map.entry("艮", "土"), Map.entry("坤", "土"));

    /** 六十四卦名，行=上卦、列=下卦，顺序同 TRIGRAM_NO（乾兑离震巽坎艮坤） */
    private static final String[][] HEXAGRAM = {
            {"乾为天", "天泽履", "天火同人", "天雷无妄", "天风姤", "天水讼", "天山遁", "天地否"},
            {"泽天夬", "兑为泽", "泽火革", "泽雷随", "泽风大过", "泽水困", "泽山咸", "泽地萃"},
            {"火天大有", "火泽睽", "离为火", "火雷噬嗑", "火风鼎", "火水未济", "火山旅", "火地晋"},
            {"雷天大壮", "雷泽归妹", "雷火丰", "震为雷", "雷风恒", "雷水解", "雷山小过", "雷地豫"},
            {"风天小畜", "风泽中孚", "风火家人", "风雷益", "巽为风", "风水涣", "风山渐", "风地观"},
            {"水天需", "水泽节", "水火既济", "水雷屯", "水风井", "坎为水", "水山蹇", "水地比"},
            {"山天大畜", "山泽损", "山火贲", "山雷颐", "山风蛊", "山水蒙", "艮为山", "山地剥"},
            {"地天泰", "地泽临", "地火明夷", "地雷复", "地风升", "地水师", "地山谦", "坤为地"}};

    private static final String[] TAROT = {
            "愚者", "魔术师", "女祭司", "女皇", "皇帝", "教皇", "恋人", "战车", "力量", "隐者", "命运之轮",
            "正义", "倒吊人", "死神", "节制", "恶魔", "高塔", "星星", "月亮", "太阳", "审判", "世界"};

    private static final String[] JIANCHU = {"建", "除", "满", "平", "定", "执", "破", "危", "成", "收", "开", "闭"};
    /** 建除十二神对应的吉凶 */
    private static final Map<String, String> JIANCHU_LUCK = Map.ofEntries(
            Map.entry("建", "平"), Map.entry("除", "吉"), Map.entry("满", "吉"), Map.entry("平", "平"),
            Map.entry("定", "吉"), Map.entry("执", "平"), Map.entry("破", "凶"), Map.entry("危", "凶"),
            Map.entry("成", "吉"), Map.entry("收", "平"), Map.entry("开", "吉"), Map.entry("闭", "凶"));

    // ==================== 对外入口 ====================

    public static Oracle cast(RandomMethod method, LocalDate date, int min, int max, Random random) {
        return switch (method) {
            case ICHING -> iching(min, max, random);
            case FIVE_ELEMENTS -> fiveElements(date, min, max);
            case BAZI -> bazi(date, min, max);
            case ZODIAC -> zodiac(date, min, max);
            case TAROT -> tarot(min, max, random);
            case ALMANAC -> almanac(date, min, max);
            default -> new Oracle("随机", method.getDescription(), Map.of());
        };
    }

    // ==================== 易经起卦 ====================

    private static Oracle iching(int min, int max, Random random) {
        int[] yao = new int[6];
        int oldCount = 0;
        StringBuilder bian = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int coins = 0;
            for (int c = 0; c < 3; c++) {
                coins += random.nextBoolean() ? 3 : 2; // 字为 3、背为 2
            }
            yao[i] = coins; // 6 老阴 / 7 少阳 / 8 少阴 / 9 老阳
            if (coins == 6 || coins == 9) {
                oldCount++;
                if (bian.length() > 0) {
                    bian.append("、");
                }
                bian.append(ordinal(i + 1)).append("爻");
            }
        }
        int lower = 0;
        int upper = 0;
        for (int i = 0; i < 3; i++) {
            if (yao[i] == 7 || yao[i] == 9) {
                lower |= 1 << i;
            }
            if (yao[i + 3] == 7 || yao[i + 3] == 9) {
                upper |= 1 << i;
            }
        }
        String upperName = TRIGRAM[upper];
        String lowerName = TRIGRAM[lower];
        int row = TRIGRAM_NO.get(upperName) - 1;
        int col = TRIGRAM_NO.get(lowerName) - 1;
        String hexagram = HEXAGRAM[row][col];
        String element = TRIGRAM_ELEMENT.get(upperName);
        int[] tails = HETU.getOrDefault(element, new int[]{1, 6});

        Map<Integer, Double> weights = new HashMap<>();
        for (int n = min; n <= max; n++) {
            double w = 1.0;
            int tail = n % 10;
            if (tail == tails[0] || tail == tails[1]) {
                w += 1.4;
            }
            // 变爻对应序位加权：爻位 1-6 映射到号码区间的六段
            if (oldCount > 0) {
                int seg = Math.min(5, (int) Math.floor((n - min) * 6.0 / Math.max(1, max - min + 1)));
                if (isOldYao(yao, seg)) {
                    w += 0.9;
                }
            }
            weights.put(n, w);
        }
        String narrative = String.format(
                "铜钱六掷，得上卦%s（属%s）、下卦%s，本卦《%s》。%s取 %d、%d 尾之数为重，结果仅供娱乐。",
                upperName, element, lowerName, hexagram,
                oldCount > 0 ? "变爻在" + bian + "，变爻所居之段再加权；" : "六爻安静，无变爻；",
                tails[0], tails[1]);
        return new Oracle("易经起卦", narrative, weights);
    }

    private static boolean isOldYao(int[] yao, int segment) {
        int idx = Math.min(5, Math.max(0, segment));
        return yao[idx] == 6 || yao[idx] == 9;
    }

    private static String ordinal(int i) {
        return switch (i) {
            case 1 -> "初";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            default -> "上";
        };
    }

    // ==================== 五行生克 ====================

    private static Oracle fiveElements(LocalDate date, int min, int max) {
        int dayGanZhi = dayGanZhiIndex(date);
        String gan = GAN[dayGanZhi % 10];
        String element = GAN_ELEMENT[dayGanZhi % 10];
        String produced = SHENG.get(element);          // 我生
        String producer = producerOf(element);         // 生我

        Map<Integer, Double> weights = new HashMap<>();
        for (int n = min; n <= max; n++) {
            int tail = n % 10;
            double w = 1.0;
            if (inHetu(element, tail)) {
                w += 1.2;
            }
            if (inHetu(produced, tail)) {
                w += 1.0;
            }
            if (inHetu(producer, tail)) {
                w += 1.0;
            }
            weights.put(n, w);
        }
        String narrative = String.format(
                "今日日柱%s，日主属%s。%s生%s、%s生%s，三者之气相续。取 %s（%s）、%s（%s）、%s（%s）之数为重，结果仅供娱乐。",
                gan + ZHI[dayGanZhi % 12], element,
                producer, element, element, produced,
                hetuText(producer), producer, hetuText(element), element, hetuText(produced), produced);
        return new Oracle("五行生克", narrative, weights);
    }

    private static String producerOf(String element) {
        for (Map.Entry<String, String> e : SHENG.entrySet()) {
            if (e.getValue().equals(element)) {
                return e.getKey();
            }
        }
        return element;
    }

    private static boolean inHetu(String element, int tail) {
        int[] tails = HETU.get(element);
        return tails != null && (tail == tails[0] || tail == tails[1]);
    }

    private static String hetuText(String element) {
        int[] tails = HETU.get(element);
        return tails == null ? "-" : tails[0] + "、" + tails[1];
    }

    // ==================== 四柱八字 ====================

    private static Oracle bazi(LocalDate date, int min, int max) {
        int yearGanZhi = ((date.getYear() - 4) % 60 + 60) % 60;
        int dayGanZhi = dayGanZhiIndex(date);

        Map<String, Integer> counter = new HashMap<>();
        addElement(counter, GAN_ELEMENT[yearGanZhi % 10], 2);
        addElement(counter, ZHI_ELEMENT[yearGanZhi % 12], 1);
        addElement(counter, GAN_ELEMENT[dayGanZhi % 10], 2);
        addElement(counter, ZHI_ELEMENT[dayGanZhi % 12], 1);

        String weakest = "水";
        String strongest = "水";
        int minCount = Integer.MAX_VALUE;
        int maxCount = -1;
        for (String el : List.of("木", "火", "土", "金", "水")) {
            int c = counter.getOrDefault(el, 0);
            if (c < minCount) {
                minCount = c;
                weakest = el;
            }
            if (c > maxCount) {
                maxCount = c;
                strongest = el;
            }
        }

        Map<Integer, Double> weights = new HashMap<>();
        for (int n = min; n <= max; n++) {
            int tail = n % 10;
            double w = 1.0;
            if (inHetu(weakest, tail)) {
                w += 1.6;
            }
            if (inHetu(strongest, tail)) {
                w -= 0.25;
            }
            weights.put(n, Math.max(0.3, w));
        }
        String narrative = String.format(
                "年柱%s、日柱%s，五行%s最旺、%s最弱。按「缺则补之、旺则泄之」，%s数（%s尾）加权，%s数降权。结果仅供娱乐。",
                GAN[yearGanZhi % 10] + ZHI[yearGanZhi % 12],
                GAN[dayGanZhi % 10] + ZHI[dayGanZhi % 12],
                strongest, weakest, weakest, hetuText(weakest), strongest);
        return new Oracle("四柱八字", narrative, weights);
    }

    private static void addElement(Map<String, Integer> counter, String element, int weight) {
        counter.merge(element, weight, Integer::sum);
    }

    // ==================== 生肖六合 ====================

    private static Oracle zodiac(LocalDate date, int min, int max) {
        int dayZhi = dayGanZhiIndex(date) % 12;
        int liuhe = (dayZhi + 1) % 12;                 // 六合：子丑、寅亥… 简化为相邻对冲配对
        int sanhe1 = (dayZhi + 4) % 12;
        int sanhe2 = (dayZhi + 8) % 12;

        Map<Integer, Double> weights = new HashMap<>();
        int span = max - min + 1;
        for (int n = min; n <= max; n++) {
            double w = 1.0;
            int slot = (int) Math.floor((n - min) * 12.0 / Math.max(1, span));
            if (slot == liuhe) {
                w += 1.5;
            }
            if (slot == sanhe1 || slot == sanhe2) {
                w += 1.0;
            }
            weights.put(n, w);
        }
        String narrative = String.format(
                "今日地支%s、属%s。六合在%s（%s），三合在%s、%s。按十二分位取段加权，%s段最重。结果仅供娱乐。",
                ZHI[dayZhi], ZODIAC[dayZhi],
                ZHI[liuhe], ZODIAC[liuhe], ZHI[sanhe1], ZHI[sanhe2],
                ZHI[liuhe]);
        return new Oracle("生肖六合", narrative, weights);
    }

    // ==================== 塔罗牌阵 ====================

    private static Oracle tarot(int min, int max, Random random) {
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < TAROT.length; i++) {
            deck.add(i);
        }
        for (int i = deck.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Integer tmp = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, tmp);
        }
        int[] picked = {deck.get(0), deck.get(1), deck.get(2)};

        Map<Integer, Double> weights = new HashMap<>();
        int span = max - min + 1;
        for (int n = min; n <= max; n++) {
            double w = 1.0;
            for (int card : picked) {
                // 每张牌在号码池上投一个高斯状影响圈
                double center = min + ((card * 7) % Math.max(1, span));
                double dist = Math.abs(n - center) / Math.max(1.0, span / 6.0);
                w += 0.9 * Math.exp(-dist * dist);
            }
            weights.put(n, w);
        }
        String narrative = String.format(
                "洗牌三巡，抽得《%s》《%s》《%s》，牌数 %d、%d、%d。按牌序在号码池上投影，牌面附近之数权重更高。结果仅供娱乐。",
                TAROT[picked[0]], TAROT[picked[1]], TAROT[picked[2]], picked[0], picked[1], picked[2]);
        return new Oracle("塔罗牌阵", narrative, weights);
    }

    // ==================== 黄历吉数 ====================

    private static Oracle almanac(LocalDate date, int min, int max) {
        int dayGanZhi = dayGanZhiIndex(date);
        int jianchuIdx = dayGanZhi % 12;
        String shen = JIANCHU[jianchuIdx];
        String luck = JIANCHU_LUCK.getOrDefault(shen, "平");
        String element = GAN_ELEMENT[dayGanZhi % 10];
        int[] tails = HETU.getOrDefault(element, new int[]{1, 6});
        boolean good = "吉".equals(luck);

        Map<Integer, Double> weights = new HashMap<>();
        for (int n = min; n <= max; n++) {
            int tail = n % 10;
            double w = 1.0;
            if (good && (tail == tails[0] || tail == tails[1])) {
                w += 1.5;
            }
            if (!good && (tail == tails[0] || tail == tails[1])) {
                w -= 0.2;
            }
            // 破日、危日取「破旧立新」之意，反取冷号
            if (!good && n % 3 == 0) {
                w += 0.8;
            }
            weights.put(n, Math.max(0.3, w));
        }
        String narrative = String.format(
                "今日%s日，值「%s」神，属%s日。%s取 %d、%d 尾为吉数；%s结果仅供娱乐。",
                GAN[dayGanZhi % 10] + ZHI[dayGanZhi % 12], shen, luck,
                good ? "吉神在位，" : "凶神当值，宜守不宜进，反取三合之数，",
                tails[0], tails[1],
                good ? "" : "破日主破旧立新，避开当日吉数而取余数；");
        return new Oracle("黄历吉数", narrative, weights);
    }

    // ==================== 干支推算 ====================

    /**
     * 日柱干支序号（0 = 甲子）。
     * 以儒略日推算：JDN 2451545（2000-01-01）为戊午日，序 54。
     */
    static int dayGanZhiIndex(LocalDate date) {
        long jdn = julianDay(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        return (int) ((jdn + 49) % 60);
    }

    private static long julianDay(int year, int month, int day) {
        long a = (14 - month) / 12;
        long y = year + 4800L - a;
        long m = month + 12L * a - 3;
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
    }

    /** 供前端/调试展示：今日干支与建除神 */
    public static String todayAlmanac(LocalDate date) {
        int idx = dayGanZhiIndex(date);
        return GAN[idx % 10] + ZHI[idx % 12] + "日 · " + JIANCHU[idx % 12] + "神";
    }
}
