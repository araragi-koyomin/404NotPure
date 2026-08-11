package com.example.tomatomall.repository;

import com.example.tomatomall.dto.ProductPageQuery;
import com.example.tomatomall.vo.ProductPageVO;
import com.example.tomatomall.vo.ProductSummaryVO;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class ProductPageRepository {

    private static final String AUTHOR_ITEM = "作者";

    @PersistenceContext
    private EntityManager entityManager;

    public ProductPageVO findPage(ProductPageQuery pageQuery) {
        SqlParts sqlParts = buildSqlParts(pageQuery);
        Query countQuery = entityManager.createNativeQuery("select count(*) from products p " + sqlParts.whereClause);
        bindFilters(countQuery, pageQuery);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        String dataSql = "select p.product_id, p.title, p.price, p.rate, p.cover, p.category "
                + "from products p " + sqlParts.whereClause + sqlParts.orderClause;
        Query dataQuery = entityManager.createNativeQuery(dataSql)
                .setFirstResult(pageQuery.getOffset())
                .setMaxResults(pageQuery.getSize());
        bindFilters(dataQuery, pageQuery);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        List<Integer> productIds = rows.stream()
                .map(row -> ((Number) row[0]).intValue())
                .collect(Collectors.toList());
        Map<Integer, String> authors = findAuthors(productIds);

        List<ProductSummaryVO> items = rows.stream()
                .map(row -> new ProductSummaryVO(
                        ((Number) row[0]).intValue(),
                        (String) row[1],
                        (BigDecimal) row[2],
                        row[3] == null ? null : ((Number) row[3]).doubleValue(),
                        (String) row[4],
                        (String) row[5],
                        authors.get(((Number) row[0]).intValue())
                ))
                .collect(Collectors.toList());

        int totalPages = totalElements == 0
                ? 0
                : (int) ((totalElements + pageQuery.getSize() - 1) / pageQuery.getSize());
        return new ProductPageVO(items, pageQuery.getPage(), pageQuery.getSize(), totalElements, totalPages);
    }

    private SqlParts buildSqlParts(ProductPageQuery pageQuery) {
        List<String> predicates = new ArrayList<>();
        if (pageQuery.getKeyword() != null) {
            predicates.add("(p.title like :keyword escape '!' or exists ("
                    + "select 1 from product_specifications keyword_spec "
                    + "where keyword_spec.product_id = p.product_id "
                    + "and keyword_spec.item = :authorItem "
                    + "and keyword_spec.value like :keyword escape '!'))");
        }
        if (!pageQuery.getCategories().isEmpty()) {
            List<String> categoryParameters = new ArrayList<>();
            int index = 0;
            for (String ignored : pageQuery.getCategories()) {
                categoryParameters.add(":category" + index++);
            }
            predicates.add("p.category in (" + String.join(",", categoryParameters) + ")");
        }
        String whereClause = predicates.isEmpty() ? "" : "where " + String.join(" and ", predicates) + " ";

        String field = "p." + ("id".equals(pageQuery.getSortField()) ? "product_id" : pageQuery.getSortField());
        String direction = pageQuery.isAscending() ? "asc" : "desc";
        StringBuilder order = new StringBuilder("order by ");
        if (!"id".equals(pageQuery.getSortField())) {
            order.append(field).append(" is null asc, ")
                    .append(field).append(' ').append(direction).append(", p.product_id asc");
        } else {
            order.append(field).append(' ').append(direction);
        }
        return new SqlParts(whereClause, order.toString());
    }

    private void bindFilters(Query query, ProductPageQuery pageQuery) {
        if (pageQuery.getKeyword() != null) {
            query.setParameter("keyword", "%" + escapeLike(pageQuery.getKeyword()) + "%");
            query.setParameter("authorItem", AUTHOR_ITEM);
        }
        int index = 0;
        for (String category : pageQuery.getCategories()) {
            query.setParameter("category" + index++, category);
        }
    }

    private Map<Integer, String> findAuthors(List<Integer> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> idParameters = new ArrayList<>();
        for (int index = 0; index < productIds.size(); index++) {
            idParameters.add(":productId" + index);
        }
        String sql = "select chosen.product_id, specification.value "
                + "from (select product_id, min(id) as specification_id "
                + "from product_specifications where item = :authorItem "
                + "and product_id in (" + String.join(",", idParameters) + ") group by product_id) chosen "
                + "join product_specifications specification on specification.id = chosen.specification_id";
        Query query = entityManager.createNativeQuery(sql).setParameter("authorItem", AUTHOR_ITEM);
        for (int index = 0; index < productIds.size(); index++) {
            query.setParameter("productId" + index, productIds.get(index));
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        Map<Integer, String> authors = new LinkedHashMap<>();
        for (Object[] row : rows) {
            authors.put(((Number) row[0]).intValue(), (String) row[1]);
        }
        return authors;
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static final class SqlParts {
        private final String whereClause;
        private final String orderClause;

        private SqlParts(String whereClause, String orderClause) {
            this.whereClause = whereClause;
            this.orderClause = orderClause;
        }
    }
}
