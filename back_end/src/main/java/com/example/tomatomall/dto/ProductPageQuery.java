package com.example.tomatomall.dto;

import com.example.tomatomall.exception.InvalidProductPageRequestException;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProductPageQuery {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private static final Set<String> ALLOWED_CATEGORIES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "literature", "biography", "history", "philosophy", "religion", "art", "design",
            "science", "computer", "internet", "medical", "health", "education", "exam",
            "economics", "management", "politics", "law", "social", "travel", "geography", "children"
    )));

    private static final Set<String> ALLOWED_SORTS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "id,asc", "id,desc", "rate,desc", "price,asc", "price,desc", "title,asc"
    )));

    private final int page;
    private final int size;
    private final String keyword;
    private final Set<String> categories;
    private final String sortField;
    private final boolean ascending;

    private ProductPageQuery(int page, int size, String keyword, Set<String> categories,
                             String sortField, boolean ascending) {
        this.page = page;
        this.size = size;
        this.keyword = keyword;
        this.categories = categories;
        this.sortField = sortField;
        this.ascending = ascending;
    }

    public static ProductPageQuery from(String pageValue, String sizeValue, String keywordValue,
                                        String categoriesValue, String sortValue) {
        int page = parsePositiveInteger(pageValue, DEFAULT_PAGE, "page");
        int size = parsePositiveInteger(sizeValue, DEFAULT_SIZE, "size");
        if (size > MAX_SIZE) {
            throw invalid("size 必须小于或等于 " + MAX_SIZE);
        }

        String keyword = normalizeKeyword(keywordValue);
        Set<String> categories = parseCategories(categoriesValue);
        String sort = normalizeSort(sortValue);
        String[] sortParts = sort.split(",", 2);
        return new ProductPageQuery(page, size, keyword, categories, sortParts[0], "asc".equals(sortParts[1]));
    }

    private static int parsePositiveInteger(String value, int defaultValue, String name) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 1) {
                throw invalid(name + " 必须是大于 0 的整数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid(name + " 必须是整数");
        }
    }

    private static String normalizeKeyword(String value) {
        if (value == null) {
            return null;
        }
        String keyword = stripUnicodeSpaces(value);
        if (keyword.isEmpty()) {
            return null;
        }
        if (keyword.codePointCount(0, keyword.length()) > MAX_KEYWORD_LENGTH) {
            throw invalid("keyword 长度不能超过 " + MAX_KEYWORD_LENGTH + " 个字符");
        }
        return keyword;
    }

    private static String stripUnicodeSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isUnicodeSpace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isUnicodeSpace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isUnicodeSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static Set<String> parseCategories(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> categories = Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .map(category -> category.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (categories.contains("") || !ALLOWED_CATEGORIES.containsAll(categories)) {
            throw invalid("categories 包含不支持的分类代码");
        }
        return Collections.unmodifiableSet(categories);
    }

    private static String normalizeSort(String value) {
        String sort = value == null || value.trim().isEmpty()
                ? "id,asc"
                : value.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SORTS.contains(sort)) {
            throw invalid("sort 只支持 id、rate、price 或 title 的预定义排序");
        }
        return sort;
    }

    private static InvalidProductPageRequestException invalid(String message) {
        return new InvalidProductPageRequestException(message);
    }

    public int getPage() { return page; }
    public int getSize() { return size; }
    public int getOffset() {
        long offset = ((long) page - 1L) * size;
        return offset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset;
    }
    public String getKeyword() { return keyword; }
    public Set<String> getCategories() { return categories; }
    public String getSortField() { return sortField; }
    public boolean isAscending() { return ascending; }
}
