package com.ttdemo.modules.random;

/**
 * 随机预测方法。所有方法只做号码生成，不承诺任何结果。
 * <p>
 * 分级：BASIC 组永久免费；STATS / MYSTIC 组为会员专享，非会员每天有固定免费试用次数。
 */
public enum RandomMethod {

    // ---------- 基础随机（免费） ----------
    PURE_RANDOM("纯随机", "在号码池内均匀随机选取，不做任何偏好。适合想要完全随机结果的场景。", Group.BASIC, false),
    BALANCED("均衡分布", "强制三区覆盖（低/中/高区各至少 1 个），且奇偶与大小尽量接近平衡。", Group.BASIC, false),
    GOLDEN("黄金分割", "按黄金比例（0.382 / 0.618）把号码池切成低中高三段，按比例取号。", Group.BASIC, false),

    // ---------- 统计策略（会员） ----------
    HOT("热号追踪", "统计近 N 期每个号码的出现频次，出现越多的号码权重越高，按权重加权抽取。", Group.STATS, true),
    COLD("冷号回补", "按号码的遗漏期数加权，越久没有出现的号码权重越高，假设存在均值回归。", Group.STATS, true),
    BALANCED_HOT("冷热均衡", "一半取近期热号、一半取深度遗漏号，兼顾惯性与回补两种假设。", Group.STATS, true),
    OMISSION("极限遗漏", "直接取遗漏期数最长的若干号码，不做随机化，属于确定性策略。", Group.STATS, true),
    MARKOV("转移概率", "基于最近一期号码与历史共现次数加权，捕捉「上期出了哪些号、下期容易带出哪些号」。", Group.STATS, true),
    SUM_TARGET("和值锚定", "在指定和值区间内随机组合号码，用拒绝采样保证和值落在目标范围。", Group.STATS, true),

    // ---------- 玄学占卜（会员） ----------
    ICHING("易经起卦", "以三枚铜钱掷六次成卦，得本卦与变爻，按卦序与河图数给号码加权。卦象每日每注不同，结果仅作娱乐。", Group.MYSTIC, true),
    FIVE_ELEMENTS("五行生克", "按当日干支定本命五行，循「木生火、火生土、土生金、金生水、水生木」的相生链，取相生之行对应河图数。", Group.MYSTIC, true),
    BAZI("四柱八字", "取年柱、日柱干支推出五行多寡，按「缺则补、旺则泄」的思路给对应数字加权。", Group.MYSTIC, true),
    ZODIAC("生肖六合", "按当日地支定生肖，取六合与三合生肖对应地支，映射到号码区间分层取号。", Group.MYSTIC, true),
    TAROT("塔罗牌阵", "从 22 张大阿卡纳中洗牌抽三张，以牌序数字构成加权曲线，牌面越大权重越集中。", Group.MYSTIC, true),
    ALMANAC("黄历吉数", "按当日干支查建除十二神与宜忌，取当日吉神方位对应的河图数作为高权号码。", Group.MYSTIC, true);

    /** 方法分组，用于前端分栏展示 */
    public enum Group {
        BASIC("基础随机", "完全免费，随时可用"),
        STATS("统计策略", "基于历史开奖数据的统计型选号"),
        MYSTIC("玄学占卜", "以卦象、干支、牌阵等传统术数生成，仅供娱乐");

        private final String title;
        private final String subtitle;

        Group(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }
    }

    private final String title;
    private final String description;
    private final Group group;
    private final boolean vip;

    RandomMethod(String title, String description, Group group, boolean vip) {
        this.title = title;
        this.description = description;
        this.group = group;
        this.vip = vip;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Group getGroup() {
        return group;
    }

    public boolean isVip() {
        return vip;
    }

    public boolean isMystic() {
        return group == Group.MYSTIC;
    }

    public static RandomMethod of(String code) {
        if (code == null || code.isBlank()) {
            return PURE_RANDOM;
        }
        for (RandomMethod method : values()) {
            if (method.name().equalsIgnoreCase(code)) {
                return method;
            }
        }
        return PURE_RANDOM;
    }
}
