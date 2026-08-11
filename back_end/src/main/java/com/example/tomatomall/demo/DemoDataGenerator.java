package com.example.tomatomall.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DemoDataGenerator {

    private static final List<ClassicBook> CLASSICS = List.of(
            classic("傲慢与偏见", "简·奥斯汀", "literature", 1813),
            classic("简·爱", "夏洛蒂·勃朗特", "literature", 1847),
            classic("呼啸山庄", "艾米莉·勃朗特", "literature", 1847),
            classic("远大前程", "查尔斯·狄更斯", "literature", 1861),
            classic("双城记", "查尔斯·狄更斯", "literature", 1859),
            classic("爱丽丝梦游仙境", "刘易斯·卡罗尔", "children", 1865),
            classic("绿野仙踪", "莱曼·弗兰克·鲍姆", "children", 1900),
            classic("白鲸", "赫尔曼·梅尔维尔", "literature", 1851),
            classic("小妇人", "路易莎·梅·奥尔科特", "literature", 1868),
            classic("金银岛", "罗伯特·路易斯·史蒂文森", "literature", 1883),
            classic("时间机器", "赫伯特·乔治·威尔斯", "science", 1895),
            classic("世界大战", "赫伯特·乔治·威尔斯", "literature", 1898),
            classic("弗兰肯斯坦", "玛丽·雪莱", "literature", 1818),
            classic("堂吉诃德", "米格尔·德·塞万提斯", "literature", 1605),
            classic("神曲", "但丁·阿利吉耶里", "literature", 1321),
            classic("奥德赛", "荷马", "literature", -700),
            classic("理想国", "柏拉图", "philosophy", -375),
            classic("孙子兵法", "孙武", "politics", -500),
            classic("红楼梦", "曹雪芹", "literature", 1791),
            classic("海底两万里", "儒勒·凡尔纳", "literature", 1870)
    );

    private static final String[] CATEGORIES = {
            "literature", "biography", "philosophy", "art", "science", "computer",
            "medical", "education", "economics", "politics", "social", "travel", "children"
    };

    private static final String[] ADJECTIVES = {
            "沉静", "遥远", "透明", "微光", "深蓝", "温柔", "漫长", "清醒", "隐秘", "灿烂",
            "古老", "崭新", "缓慢", "明亮", "自由", "孤独", "丰饶", "无声", "轻盈", "坚定"
    };

    private static final String[] SUBJECTS = {
            "山海", "星河", "城邦", "纸舟", "灯塔", "花园", "回廊", "旅人", "钟摆", "信笺",
            "岛屿", "麦田", "算法", "博物馆", "航线", "森林", "课堂", "街角", "时间", "群峰"
    };

    private static final String[] SUFFIXES = {"手记", "纪事", "地图", "来信", "方法"};
    private static final String[] PUBLISHERS = {
            "番茄演示出版社", "远山出版实验室", "纸舟文化", "星河书房", "城市阅读社",
            "开源知识出版社", "青禾教育", "灯塔人文社"
    };
    private static final String[] BINDINGS = {"平装", "精装", "线装", "函套装"};
    private static final String[] AUTHORS = {
            "林知远", "周明溪", "沈亦安", "顾南星", "陆清和", "苏言川", "叶书宁", "江望舒",
            "许闻舟", "程景行", "唐予安", "宋知白", "陈砚秋", "谢云帆", "孟初晴", "秦思远"
    };

    public DemoDataset generate(DemoDataImportConfig config) {
        List<DemoBook> books = new ArrayList<>(config.bookCount());
        for (int index = 0; index < config.bookCount(); index++) {
            books.add(index < CLASSICS.size()
                    ? publicDomainBook(index + 1, CLASSICS.get(index))
                    : syntheticBook(index + 1, config.seed()));
        }

        List<DemoUser> users = new ArrayList<>(config.bulkUserCount() + 2);
        users.add(new DemoUser("demo_admin", "本地演示管理员", "ADMIN",
                "demo-admin@example.invalid", "本地演示环境", 0));
        users.add(new DemoUser("demo_user", "本地演示用户", "USER",
                "demo-user@example.invalid", "本地演示环境", 100));
        for (int index = 1; index <= config.bulkUserCount(); index++) {
            users.add(new DemoUser(
                    String.format(Locale.ROOT, "load_user_%04d", index),
                    String.format(Locale.ROOT, "规模用户%04d", index),
                    "USER",
                    String.format(Locale.ROOT, "load-user-%04d@example.invalid", index),
                    "DATA-001 规模数据",
                    index % 200
            ));
        }

        List<DemoAdvertisement> advertisements = new ArrayList<>();
        int advertisementCount = Math.min(6, books.size());
        for (int index = 1; index <= advertisementCount; index++) {
            DemoBook book = books.get(index - 1);
            advertisements.add(new DemoAdvertisement(
                    index,
                    "本周阅读推荐 " + index,
                    "从《" + book.title() + "》开始一次稳定、可重复的本地演示。",
                    assetUrl(config.seed(), "advertisements/ad-" + twoDigits(index) + ".svg"),
                    book.ordinal()
            ));
        }

        return new DemoDataset(config.seed(), books, users, advertisements);
    }

    private DemoBook publicDomainBook(int ordinal, ClassicBook classic) {
        String base = "books/book-" + fourDigits(ordinal);
        String cover = assetUrl(DemoDataImportConfig.DEFAULT_SEED, base + "-cover.svg");
        String content = assetUrl(DemoDataImportConfig.DEFAULT_SEED, base + "-detail-01.svg");
        String year = classic.year() < 0 ? "约公元前" + Math.abs(classic.year()) + "年" : classic.year() + "年";
        return new DemoBook(
                ordinal,
                true,
                classic.title(),
                price(26.00 + ordinal * 1.15),
                4.1 + (ordinal % 8) * 0.1,
                "公版经典书目。本项目仅使用书名、作者和年代等事实信息，简介为本地演示重新编写。",
                "适合用于商品详情、库存、广告和缓存链路的稳定演示，不代表任何特定商业版本。",
                cover,
                classic.category(),
                List.of(
                        new DemoSpecification("作者", classic.author()),
                        new DemoSpecification("出版社", PUBLISHERS[ordinal % PUBLISHERS.length]),
                        new DemoSpecification("装帧", BINDINGS[ordinal % BINDINGS.length]),
                        new DemoSpecification("首次出版", year)
                ),
                List.of(content),
                40 + ordinal * 3
        );
    }

    private DemoBook syntheticBook(int ordinal, long seed) {
        int syntheticIndex = ordinal - CLASSICS.size() - 1;
        int combinations = ADJECTIVES.length * SUBJECTS.length * SUFFIXES.length;
        int position = Math.floorMod((int) (seed % combinations) + syntheticIndex * 37, combinations);
        int adjectiveIndex = position / (SUBJECTS.length * SUFFIXES.length);
        int remainder = position % (SUBJECTS.length * SUFFIXES.length);
        int subjectIndex = remainder / SUFFIXES.length;
        int suffixIndex = remainder % SUFFIXES.length;
        String title = ADJECTIVES[adjectiveIndex] + SUBJECTS[subjectIndex] + SUFFIXES[suffixIndex];
        String category = CATEGORIES[Math.floorMod(position + ordinal, CATEGORIES.length)];
        String author = AUTHORS[Math.floorMod(position * 3 + ordinal, AUTHORS.length)];
        int year = 2005 + Math.floorMod(position + ordinal, 20);
        String base = "books/book-" + fourDigits(ordinal);
        String cover = assetUrl(seed, base + "-cover.svg");
        String content = assetUrl(seed, base + "-detail-01.svg");
        return new DemoBook(
                ordinal,
                false,
                title,
                price(18.00 + Math.floorMod(position * 17, 6200) / 100.0),
                3.6 + Math.floorMod(position, 14) * 0.1,
                "由 DATA-001 固定种子生成的虚构书籍，用于本机页面展示和可重复数据规模测试。",
                "书名、作者和出版社均为演示组合，不对应真实出版物，也不表示真实销量或用户评价。",
                cover,
                category,
                List.of(
                        new DemoSpecification("作者", author),
                        new DemoSpecification("出版社", PUBLISHERS[Math.floorMod(position, PUBLISHERS.length)]),
                        new DemoSpecification("装帧", BINDINGS[Math.floorMod(position, BINDINGS.length)]),
                        new DemoSpecification("出版年份", year + "年")
                ),
                List.of(content),
                20 + Math.floorMod(position * 11, 180)
        );
    }

    private static ClassicBook classic(String title, String author, String category, int year) {
        return new ClassicBook(title, author, category, year);
    }

    private static BigDecimal price(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String assetUrl(long seed, String relativePath) {
        return "/demo-data/generated/seed-" + seed + "/" + relativePath;
    }

    private static String fourDigits(int value) {
        return String.format(Locale.ROOT, "%04d", value);
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", value);
    }

    private record ClassicBook(String title, String author, String category, int year) {
    }
}
