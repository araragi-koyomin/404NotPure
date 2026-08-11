package com.example.tomatomall.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DemoAssetWriter {

    private static final String URL_PREFIX = "/demo-data/generated/";

    public DemoAssetSummary write(DemoDataset dataset, Path assetRoot) throws IOException {
        if (dataset == null) {
            throw new IllegalArgumentException("演示数据不能为空");
        }
        if (assetRoot == null) {
            throw new IllegalArgumentException("SVG 输出目录不能为空");
        }
        Path normalizedRoot = assetRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);

        int coverCount = 0;
        int detailCount = 0;
        for (DemoBook book : dataset.books()) {
            long assetSeed = book.publicDomain()
                    ? DemoDataImportConfig.DEFAULT_SEED
                    : dataset.seed();
            writeSvg(normalizedRoot, book.coverUrl(), coverSvg(book, assetSeed));
            coverCount++;
            for (int index = 0; index < book.contentImageUrls().size(); index++) {
                writeSvg(normalizedRoot, book.contentImageUrls().get(index),
                        detailSvg(book, assetSeed, index + 1));
                detailCount++;
            }
        }

        int advertisementCount = 0;
        for (DemoAdvertisement advertisement : dataset.advertisements()) {
            writeSvg(normalizedRoot, advertisement.imageUrl(), advertisementSvg(advertisement, dataset));
            advertisementCount++;
        }
        return new DemoAssetSummary(coverCount, detailCount, advertisementCount);
    }

    private void writeSvg(Path root, String publicUrl, String svg) throws IOException {
        if (publicUrl == null || !publicUrl.startsWith(URL_PREFIX)) {
            throw new IllegalArgumentException("演示资源 URL 必须位于 " + URL_PREFIX);
        }
        String relative = publicUrl.substring(URL_PREFIX.length());
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("拒绝写出演示资源目录: " + publicUrl);
        }
        Files.createDirectories(target.getParent());
        Files.writeString(
                target,
                svg,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private String coverSvg(DemoBook book, long seed) {
        Palette palette = palette(book.ordinal(), seed);
        String badge = book.publicDomain() ? "PUBLIC DOMAIN CLASSIC" : "DETERMINISTIC DEMO BOOK";
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="720" height="1000" viewBox="0 0 720 1000">
                  <rect width="720" height="1000" fill="%s"/>
                  <circle cx="585" cy="165" r="180" fill="%s" opacity="0.75"/>
                  <rect x="72" y="80" width="12" height="840" rx="6" fill="%s"/>
                  <text x="112" y="160" fill="%s" font-family="sans-serif" font-size="22" letter-spacing="3">%s</text>
                  <text x="112" y="420" fill="%s" font-family="sans-serif" font-size="52" font-weight="700">%s</text>
                  <text x="112" y="490" fill="%s" font-family="sans-serif" font-size="26">%s</text>
                  <text x="112" y="865" fill="%s" font-family="sans-serif" font-size="22">TomatoMall · DATA-001</text>
                </svg>
                """.formatted(
                palette.background(), palette.accent(), palette.line(), palette.text(),
                escapeXml(badge), palette.text(), escapeXml(shortText(book.title(), 12)),
                palette.text(), escapeXml(categoryLabel(book.category())), palette.text()
        );
    }

    private String detailSvg(DemoBook book, long seed, int imageIndex) {
        Palette palette = palette(book.ordinal() + imageIndex * 11, seed);
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="720" viewBox="0 0 1200 720">
                  <rect width="1200" height="720" fill="%s"/>
                  <path d="M0 540 C260 390 420 700 700 500 C900 360 1040 420 1200 300 L1200 720 L0 720 Z" fill="%s"/>
                  <text x="90" y="150" fill="%s" font-family="sans-serif" font-size="28">TomatoMall 本地详情图</text>
                  <text x="90" y="310" fill="%s" font-family="sans-serif" font-size="58" font-weight="700">%s</text>
                  <text x="90" y="380" fill="%s" font-family="sans-serif" font-size="28">%s · 图片 %02d</text>
                </svg>
                """.formatted(
                palette.background(), palette.accent(), palette.text(), palette.text(),
                escapeXml(shortText(book.title(), 18)), palette.text(),
                escapeXml(categoryLabel(book.category())), imageIndex
        );
    }

    private String advertisementSvg(DemoAdvertisement advertisement, DemoDataset dataset) {
        DemoBook book = dataset.books().get(advertisement.bookOrdinal() - 1);
        Palette palette = palette(advertisement.ordinal() * 19, dataset.seed());
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="1600" height="600" viewBox="0 0 1600 600">
                  <rect width="1600" height="600" fill="%s"/>
                  <circle cx="1320" cy="120" r="300" fill="%s" opacity="0.7"/>
                  <text x="120" y="150" fill="%s" font-family="sans-serif" font-size="30" letter-spacing="4">TOMATOMALL RECOMMENDS</text>
                  <text x="120" y="310" fill="%s" font-family="sans-serif" font-size="72" font-weight="700">%s</text>
                  <text x="120" y="395" fill="%s" font-family="sans-serif" font-size="32">%s</text>
                </svg>
                """.formatted(
                palette.background(), palette.accent(), palette.text(), palette.text(),
                escapeXml(shortText(book.title(), 20)), palette.text(), escapeXml(advertisement.title())
        );
    }

    private Palette palette(int ordinal, long seed) {
        String[] backgrounds = {"#14213d", "#3d405b", "#264653", "#5f0f40", "#283618", "#4a4e69"};
        String[] accents = {"#fca311", "#e07a5f", "#2a9d8f", "#fb8b24", "#dda15e", "#c9ada7"};
        int index = Math.floorMod((int) (seed % backgrounds.length) + ordinal, backgrounds.length);
        return new Palette(backgrounds[index], accents[index], "#ffffff", "#f4f1de");
    }

    private String shortText(String value, int maxCharacters) {
        if (value.length() <= maxCharacters) {
            return value;
        }
        return value.substring(0, maxCharacters - 1) + "…";
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String categoryLabel(String code) {
        return switch (code) {
            case "literature" -> "文学小说";
            case "biography" -> "历史传记";
            case "philosophy" -> "哲学宗教";
            case "art" -> "艺术设计";
            case "science" -> "科学技术";
            case "computer" -> "计算机与互联网";
            case "medical" -> "医学与健康";
            case "education" -> "教育考试";
            case "economics" -> "经济管理";
            case "politics" -> "政治法律";
            case "social" -> "社会科学";
            case "travel" -> "旅行与地理";
            case "children" -> "儿童读物";
            default -> throw new IllegalArgumentException("不支持的演示商品分类: " + code);
        };
    }

    private record Palette(String background, String accent, String line, String text) {
    }
}
