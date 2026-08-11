package com.example.tomatomall.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class ProductPageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<Integer> productIds = new ArrayList<>();
    private String marker;

    @BeforeEach
    void setUp() {
        marker = "api002-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @AfterEach
    void cleanUp() {
        for (Integer productId : productIds) {
            jdbcTemplate.update("delete from product_content_images where product_id = ?", productId);
            jdbcTemplate.update("delete from product_specifications where product_id = ?", productId);
            jdbcTemplate.update("delete from stockpile where product_id = ?", productId);
            jdbcTemplate.update("delete from products where product_id = ?", productId);
        }
    }

    @Test
    void defaultPageReturnsTwentyLightweightSummariesAndMetadata() throws Exception {
        for (int index = 1; index <= 23; index++) {
            int productId = createProduct(
                    marker + "-book-" + String.format("%02d", index),
                    "author-" + index,
                    "literature",
                    new BigDecimal("39.90"),
                    8.0 + index / 100.0
            );
            createContentImage(productId, "/test/" + marker + "/detail-" + index + ".svg");
        }

        mockMvc.perform(get("/api/products/page").param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(23))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.items[0].author").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].price").value(39.90))
                .andExpect(jsonPath("$.data.items[0].specifications").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].contentImages").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].detail").doesNotExist());
    }

    @Test
    void keywordCanFindAProductByAuthorWithoutMatchingItsTitle() throws Exception {
        createProduct(
                "unrelated-title-" + UUID.randomUUID(),
                marker + "-writer",
                "history",
                new BigDecimal("45.00"),
                9.1
        );
        createProduct(
                "another-unrelated-title-" + UUID.randomUUID(),
                "someone-else",
                "history",
                new BigDecimal("46.00"),
                9.0
        );

        mockMvc.perform(get("/api/products/page").param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].author").value(marker + "-writer"));
    }

    @Test
    void invalidPageReturnsHttpBadRequestWithResponseEnvelope() throws Exception {
        mockMvc.perform(get("/api/products/page").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void fullWidthWhitespaceKeywordIsEquivalentToNoKeyword() throws Exception {
        int productId = createProduct(marker + "-unicode-space", marker + "-author", "literature",
                new BigDecimal("20.00"), 8.0);
        Long productCount = jdbcTemplate.queryForObject("select count(*) from products", Long.class);

        mockMvc.perform(get("/api/products/page")
                        .param("keyword", "　   ")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(productCount))
                .andExpect(jsonPath("$.data.items[?(@.id == " + productId + ")]").exists());
    }

    @Test
    void secondPageAndOutOfRangePageUseStableOneBasedPagination() throws Exception {
        List<Integer> createdIds = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            createdIds.add(createProduct(marker + "-page-" + index, "author-" + index,
                    "literature", new BigDecimal("20.00"), 8.0));
        }

        mockMvc.perform(get("/api/products/page")
                        .param("keyword", marker)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(createdIds.get(2)))
                .andExpect(jsonPath("$.data.items[1].id").value(createdIds.get(3)));

        mockMvc.perform(get("/api/products/page")
                        .param("keyword", marker)
                        .param("page", "99")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(99))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc.perform(get("/api/products/page")
                        .param("keyword", marker)
                        .param("page", String.valueOf(Integer.MAX_VALUE))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void commaSeparatedCategoriesUseRawDatabaseCodes() throws Exception {
        createProduct(marker + "-literature", "author-a", "literature", new BigDecimal("20.00"), 8.0);
        createProduct(marker + "-history", "author-b", "history", new BigDecimal("21.00"), 8.1);
        createProduct(marker + "-science", "author-c", "science", new BigDecimal("22.00"), 8.2);

        mockMvc.perform(get("/api/products/page")
                        .param("keyword", marker)
                        .param("categories", "literature,history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].category").value("literature"))
                .andExpect(jsonPath("$.data.items[1].category").value("history"));
    }

    @Test
    void supportedSortsAreStableAndPutNullValuesLast() throws Exception {
        int firstId = createProduct(marker + "-z-title", "author-z", "literature", new BigDecimal("30.00"), 9.0);
        int secondId = createProduct(marker + "-a-title", "author-a", "literature", new BigDecimal("10.00"), 9.0);
        int nullRateId = createProduct(marker + "-m-title", "author-m", "literature", new BigDecimal("20.00"), 7.0);
        jdbcTemplate.update("update products set rate = null where product_id = ?", nullRateId);

        mockMvc.perform(get("/api/products/page").param("keyword", marker).param("sort", "rate,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(firstId))
                .andExpect(jsonPath("$.data.items[1].id").value(secondId))
                .andExpect(jsonPath("$.data.items[2].id").value(nullRateId));

        mockMvc.perform(get("/api/products/page").param("keyword", marker).param("sort", "price,asc"))
                .andExpect(jsonPath("$.data.items[0].id").value(secondId))
                .andExpect(jsonPath("$.data.items[2].id").value(firstId));

        mockMvc.perform(get("/api/products/page").param("keyword", marker).param("sort", "price,desc"))
                .andExpect(jsonPath("$.data.items[0].id").value(firstId))
                .andExpect(jsonPath("$.data.items[2].id").value(secondId));

        jdbcTemplate.update("update products set price = null where product_id = ?", nullRateId);
        mockMvc.perform(get("/api/products/page").param("keyword", marker).param("sort", "price,desc"))
                .andExpect(jsonPath("$.data.items[0].id").value(firstId))
                .andExpect(jsonPath("$.data.items[1].id").value(secondId))
                .andExpect(jsonPath("$.data.items[2].id").value(nullRateId));

        mockMvc.perform(get("/api/products/page").param("keyword", marker).param("sort", "title,asc"))
                .andExpect(jsonPath("$.data.items[0].id").value(secondId))
                .andExpect(jsonPath("$.data.items[2].id").value(firstId));

        mockMvc.perform(get("/api/products/page").param("keyword", marker).param("sort", "id,desc"))
                .andExpect(jsonPath("$.data.items[0].id").value(nullRateId))
                .andExpect(jsonPath("$.data.items[2].id").value(firstId));
    }

    @Test
    void authorIsNullableAndLowestSpecificationIdWins() throws Exception {
        int productWithoutAuthor = createProduct(marker + "-no-author", "temporary", "literature",
                new BigDecimal("20.00"), 8.0);
        jdbcTemplate.update("delete from product_specifications where product_id = ? and item = '作者'", productWithoutAuthor);

        int productWithTwoAuthors = createProduct(marker + "-two-authors", "first-author", "literature",
                new BigDecimal("21.00"), 8.1);
        jdbcTemplate.update("insert into product_specifications (product_id, item, value) values (?, '作者', 'second-author')",
                productWithTwoAuthors);

        mockMvc.perform(get("/api/products/page").param("keyword", marker).param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(productWithoutAuthor))
                .andExpect(jsonPath("$.data.items[0].author").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].id").value(productWithTwoAuthors))
                .andExpect(jsonPath("$.data.items[1].author").value("first-author"));
    }

    @Test
    void invalidQueryParametersReturnHttpBadRequest() throws Exception {
        String[][] invalidParameters = {
                {"page", "not-a-number"},
                {"size", "0"},
                {"size", "101"},
                {"categories", "not-supported"},
                {"sort", "description,asc"},
                {"keyword", "x".repeat(101)}
        };
        for (String[] invalidParameter : invalidParameters) {
            mockMvc.perform(get("/api/products/page").param(invalidParameter[0], invalidParameter[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400"))
                    .andExpect(jsonPath("$.msg").isNotEmpty());
        }
    }

    @Test
    void populatedPageUsesThreeDatabaseStatementsRegardlessOfPageSize() throws Exception {
        for (int index = 1; index <= 20; index++) {
            createProduct(marker + "-query-count-" + index, "author-" + index,
                    "literature", new BigDecimal("20.00"), 8.0);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/products/page").param("keyword", marker).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(20));

        assertEquals(3L, statistics.getPrepareStatementCount(),
                "分页查询应固定为总数、当前页和作者批量查询三条 SQL");

        statistics.clear();
        mockMvc.perform(get("/api/products/page").param("keyword", marker).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        assertEquals(3L, statistics.getPrepareStatementCount(),
                "页大小从 20 改为 1 后仍应只执行三条 SQL");
    }

    @Test
    void legacyProductListRetainsAllProductVoFields() throws Exception {
        int productId = createProduct(marker + "-legacy", marker + "-author", "literature",
                new BigDecimal("39.90"), 8.8);
        createContentImage(productId, "/test/" + marker + "/legacy-detail.svg");

        MvcResult result = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode products = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
        JsonNode legacyProduct = null;
        for (JsonNode product : products) {
            if (product.path("id").asInt() == productId) {
                legacyProduct = product;
                break;
            }
        }
        assertNotNull(legacyProduct);
        assertEquals(marker + "-legacy", legacyProduct.path("title").asText());
        assertEquals(0, new BigDecimal("39.90").compareTo(legacyProduct.path("price").decimalValue()));
        assertEquals(8.8, legacyProduct.path("rate").asDouble(), 0.0001);
        assertEquals("description-" + marker, legacyProduct.path("description").asText());
        assertEquals("detail-" + marker, legacyProduct.path("detail").asText());
        assertEquals("/test/" + marker + "/cover.svg", legacyProduct.path("cover").asText());
        assertEquals("literature", legacyProduct.path("category").asText());
        assertTrue(legacyProduct.path("specifications").isArray());
        assertEquals(2, legacyProduct.path("specifications").size());
        assertTrue(legacyProduct.path("contentImages").isArray());
        assertEquals(1, legacyProduct.path("contentImages").size());
    }

    private int createProduct(
            String title,
            String author,
            String category,
            BigDecimal price,
            double rate
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into products (title, price, rate, description, detail, cover, category) "
                            + "values (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, title);
            statement.setBigDecimal(2, price);
            statement.setDouble(3, rate);
            statement.setString(4, "description-" + marker);
            statement.setString(5, "detail-" + marker);
            statement.setString(6, "/test/" + marker + "/cover.svg");
            statement.setString(7, category);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        assertNotNull(generatedId);
        int productId = generatedId.intValue();
        productIds.add(productId);
        jdbcTemplate.update(
                "insert into product_specifications (product_id, item, value) values (?, '作者', ?)",
                productId,
                author
        );
        jdbcTemplate.update(
                "insert into product_specifications (product_id, item, value) values (?, '出版社', ?)",
                productId,
                "publisher-" + marker
        );
        return productId;
    }

    private void createContentImage(int productId, String imageUrl) {
        jdbcTemplate.update(
                "insert into product_content_images (product_id, image_url) values (?, ?)",
                productId,
                imageUrl
        );
    }
}
