package com.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.product.enums.Role;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.po.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A2/A3 黑盒数据库验收。
 * 期望结果仅从四张原始表独立计算；不调用生产 Service/Mapper/统计方法。
 * 实际结果仅通过 HTTP 接口取得。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderA2A3BlackBoxDatabaseTest {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String RAW_SQL = """
            SELECT o.order_id, o.order_date, o.total_amount, o.order_status,
                   o.pay_way, o.user_id, u.name AS user_name,
                   oi.order_item_id, oi.product_id, oi.quantity, oi.item_total,
                   p.product_name, p.category, p.brand
            FROM `order` o
            LEFT JOIN `user` u ON u.user_id = o.user_id
            LEFT JOIN order_item oi ON oi.order_id = o.order_id
            LEFT JOIN product p ON p.product_id = oi.product_id
            WHERE o.order_date >= ?
              AND o.order_date < DATE_ADD(?, INTERVAL 1 DAY)
              AND o.order_status = 'completed'
            ORDER BY o.order_date DESC, o.order_id DESC,
                     oi.product_id ASC, oi.order_item_id ASC
            """;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;

    @Test
    void a2_neoTechAdmin_matchesIndependentDatabaseResult() throws Exception {
        Scenario s = adminCase();
        Oracle oracle = oracle(s);
        JsonNode expected = oracle.a2(json);
        JsonNode actual = callA2(s);

        print("A2_ADMIN_REQUEST", s.a2Request());
        print("A2_ADMIN_EXPECTED", expected);
        print("A2_ADMIN_ACTUAL", actual);
        assertResponse(actual, expected);
    }

    @Test
    void a3_neoTechAdmin_matchesA2ScopeAndIndependentDatabaseResult() throws Exception {
        Scenario s = adminCase();
        Oracle oracle = oracle(s);
        JsonNode expected = oracle.a3(json);
        JsonNode actual = callA3(s);

        print("A3_ADMIN_REQUEST", s.a3Request());
        print("A3_ADMIN_EXPECTED", expected);
        print("A3_ADMIN_ACTUAL", actual);
        assertThat(oracle.a2MatchedSum()).isEqualByComparingTo(oracle.totalSales());
        assertThat(oracle.total()).isEqualTo(oracle.orderCount());
        assertResponse(actual, expected);
    }

    @Test
    void a2_superAdmin_includesDatabaseMissingAndMismatchOrders() throws Exception {
        Scenario s = superAdminCase();
        Oracle oracle = oracle(s);
        JsonNode expected = oracle.a2(json);
        JsonNode actual = callA2(s);

        print("A2_SUPER_ADMIN_REQUEST", s.a2Request());
        print("A2_SUPER_ADMIN_EXPECTED", expected);
        print("A2_SUPER_ADMIN_ACTUAL", actual);
        assertThat(oracle.missing()).isEqualTo(1);
        assertThat(oracle.mismatch()).isEqualTo(1);
        assertResponse(actual, expected);
    }

    @Test
    void a3_superAdmin_matchesA2ScopeAndIndependentDatabaseResult() throws Exception {
        Scenario s = superAdminCase();
        Oracle oracle = oracle(s);
        JsonNode expected = oracle.a3(json);
        JsonNode actual = callA3(s);

        print("A3_SUPER_ADMIN_REQUEST", s.a3Request());
        print("A3_SUPER_ADMIN_EXPECTED", expected);
        print("A3_SUPER_ADMIN_ACTUAL", actual);
        assertThat(oracle.a2MatchedSum()).isEqualByComparingTo(oracle.totalSales());
        assertThat(oracle.total()).isEqualTo(oracle.orderCount());
        assertResponse(actual, expected);
    }

    @Test
    void a2AndA3_emptyDatabaseRange_returnContractEmptyValues() throws Exception {
        Scenario s = new Scenario(Role.SUPER_ADMIN, null,
                LocalDate.of(2035, 1, 1), LocalDate.of(2035, 1, 2),
                List.of(), List.of(), null, 1, 20);
        Oracle oracle = oracle(s);
        JsonNode expectedA2 = oracle.a2(json);
        JsonNode expectedA3 = oracle.a3(json);
        JsonNode actualA2 = callA2(s);
        JsonNode actualA3 = callA3(s);

        print("A2_EMPTY_EXPECTED", expectedA2);
        print("A2_EMPTY_ACTUAL", actualA2);
        print("A3_EMPTY_EXPECTED", expectedA3);
        print("A3_EMPTY_ACTUAL", actualA3);
        assertResponse(actualA2, expectedA2);
        assertResponse(actualA3, expectedA3);
    }

    private JsonNode callA2(Scenario s) throws Exception {
        var request = get("/api/orders")
                .param("startTime", s.start().toString())
                .param("endTime", s.end().toString())
                .param("statuses", "completed")
                .param("page", Integer.toString(s.page()))
                .param("num", Integer.toString(s.size()))
                .with(user(principal(s)));
        for (String value : s.categories()) request.param("category", value);
        for (String value : s.brands()) request.param("brand", value);
        if (s.keyword() != null) request.param("keyWord", s.keyword());
        return response(mockMvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private JsonNode callA3(Scenario s) throws Exception {
        var request = get("/api/orders/kpi")
                .param("startTime", s.start().toString())
                .param("endTime", s.end().toString())
                .with(user(principal(s)));
        for (String value : s.categories()) request.param("category", value);
        for (String value : s.brands()) request.param("brand", value);
        return response(mockMvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private Oracle oracle(Scenario s) {
        List<Map<String, Object>> rows = jdbc.queryForList(RAW_SQL, s.start(), s.end());
        Map<Long, DbOrder> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long id = num(row.get("order_id"));
            DbOrder order = grouped.computeIfAbsent(id, ignored -> new DbOrder(
                    id, dateTime(row.get("order_date")), money(row.get("total_amount")),
                    str(row.get("order_status")), str(row.get("pay_way")),
                    num(row.get("user_id")), str(row.get("user_name")), new ArrayList<>()));
            if (row.get("order_item_id") != null && row.get("product_id") != null) {
                order.items().add(new DbItem(
                        num(row.get("order_item_id")), num(row.get("product_id")),
                        num(row.get("quantity")), money(row.get("item_total")),
                        str(row.get("product_name")), str(row.get("category")),
                        str(row.get("brand"))));
            }
        }

        List<ApiOrder> selected = grouped.values().stream()
                .map(order -> convert(order, s))
                .filter(order -> order != null)
                .sorted(Comparator.comparing(ApiOrder::createTime).reversed()
                        .thenComparing(ApiOrder::orderId, Comparator.reverseOrder()))
                .toList();

        BigDecimal totalSales = selected.stream().map(ApiOrder::matchedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        int count = selected.size();
        BigDecimal average = count == 0 ? new BigDecimal("0.00")
                : totalSales.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        Set<Long> products = new LinkedHashSet<>();
        selected.forEach(order -> products.addAll(order.matchedProducts()));
        int sold = products.size();
        int hot = sold == 0 ? 0 : (int) Math.ceil(sold * 0.30d);
        int missing = s.role() == Role.ADMIN ? 0
                : (int) selected.stream().filter(order -> !order.hasDetails()).count();
        Integer mismatch = s.role() == Role.ADMIN ? null
                : (int) selected.stream().filter(order -> Boolean.TRUE.equals(order.difference())).count();
        int offset = (s.page() - 1) * s.size();
        List<ApiOrder> page = offset >= count ? List.of()
                : selected.subList(offset, Math.min(offset + s.size(), count));
        return new Oracle(s, page, count, totalSales, count, average, sold, hot, missing, mismatch);
    }

    private ApiOrder convert(DbOrder order, Scenario s) {
        List<DbItem> visible = order.items().stream()
                .filter(item -> s.role() == Role.SUPER_ADMIN
                        || item.brand().equals(s.accountBrand())).toList();
        List<DbItem> matched = visible.stream()
                .filter(item -> s.categories().isEmpty() || s.categories().contains(item.category()))
                .filter(item -> s.brands().isEmpty() || s.brands().contains(item.brand()))
                .toList();
        boolean fallback = s.role() == Role.SUPER_ADMIN && visible.isEmpty()
                && s.categories().isEmpty() && s.brands().isEmpty();
        boolean keywordOk = s.keyword() == null
                || contains(Long.toString(order.id()), s.keyword())
                || matched.stream().anyMatch(item -> contains(item.name(), s.keyword()));
        if ((!fallback && matched.isEmpty()) || !keywordOk) return null;

        BigDecimal visibleAmount = fallback ? order.amount() : sum(visible);
        BigDecimal matchedAmount = fallback ? order.amount() : sum(matched);
        Map<Long, DbItem> products = new LinkedHashMap<>();
        visible.stream().sorted(Comparator.comparingLong(DbItem::productId))
                .forEach(item -> products.putIfAbsent(item.productId(), item));
        List<DbItem> distinctProducts = new ArrayList<>(products.values());
        Boolean difference = s.role() == Role.ADMIN || visible.isEmpty() ? null
                : order.amount().subtract(sum(visible)).abs()
                .compareTo(new BigDecimal("0.01")) > 0;
        Set<Long> matchedIds = new LinkedHashSet<>();
        matched.forEach(item -> matchedIds.add(item.productId()));
        return new ApiOrder(order.id(), s.role() == Role.ADMIN ? null : order.amount(),
                s.role() == Role.ADMIN ? null : order.amount(), visibleAmount, matchedAmount,
                s.role() == Role.ADMIN ? "BRAND" : "FULL", order.status(), order.time(),
                order.userId(), order.userName(), order.payWay(), summary(distinctProducts),
                distinctProducts.size(), !visible.isEmpty(), difference, matchedIds);
    }

    private static Scenario adminCase() {
        return new Scenario(Role.ADMIN, "NeoTech",
                LocalDate.of(2025, 11, 12), LocalDate.of(2025, 11, 14),
                List.of(), List.of("NeoTech"), null, 1, 20);
    }

    private static Scenario superAdminCase() {
        return new Scenario(Role.SUPER_ADMIN, null,
                LocalDate.of(2025, 11, 13), LocalDate.of(2025, 11, 13),
                List.of(), List.of(), null, 1, 20);
    }

    private static String summary(List<DbItem> products) {
        if (products.isEmpty()) return "";
        String names = products.stream().limit(2).map(DbItem::name)
                .reduce((a, b) -> a + "、" + b).orElse("");
        return products.size() > 2 ? names + "等 " + products.size() + " 种商品" : names;
    }

    private static BigDecimal sum(List<DbItem> items) {
        return items.stream().map(DbItem::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT)
                .contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static MyUserDetails principal(Scenario s) {
        User account = User.builder().userName("black-box-user").password("unused")
                .role(s.role()).brand(s.accountBrand()).build();
        return MyUserDetails.builder().username(account.getUserName())
                .password(account.getPassword()).user(account).build();
    }

    private void assertResponse(JsonNode actual, JsonNode expected) {
        assertThat(actual.path("code").asInt())
                .as("接口没有成功，完整响应：%s", actual).isEqualTo(200);
        assertThat(actual).isEqualTo(expected);
    }

    private JsonNode response(MvcResult result) throws Exception {
        return json.readTree(new String(result.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8));
    }

    private void print(String label, Object value) throws Exception {
        System.out.println(label + "=" + (value instanceof JsonNode node
                ? json.writerWithDefaultPrettyPrinter().writeValueAsString(node) : value));
    }

    private static String str(Object value) { return value == null ? null : value.toString(); }
    private static long num(Object value) { return ((Number) value).longValue(); }
    private static BigDecimal money(Object value) {
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
    private static LocalDateTime dateTime(Object value) {
        return value instanceof LocalDateTime time ? time : ((Timestamp) value).toLocalDateTime();
    }

    private record Scenario(Role role, String accountBrand, LocalDate start, LocalDate end,
                            List<String> categories, List<String> brands, String keyword,
                            int page, int size) {
        String a2Request() {
            return "GET /api/orders?startTime=" + start + "&endTime=" + end
                    + brands.stream().map(v -> "&brand=" + v).reduce("", String::concat)
                    + "&statuses=completed&page=" + page + "&num=" + size;
        }
        String a3Request() {
            return "GET /api/orders/kpi?startTime=" + start + "&endTime=" + end
                    + brands.stream().map(v -> "&brand=" + v).reduce("", String::concat);
        }
    }

    private record DbOrder(long id, LocalDateTime time, BigDecimal amount, String status,
                           String payWay, long userId, String userName, List<DbItem> items) {}
    private record DbItem(long itemId, long productId, long quantity, BigDecimal total,
                          String name, String category, String brand) {}
    private record ApiOrder(Long orderId, BigDecimal amount, BigDecimal originalAmount,
                            BigDecimal visibleAmount, BigDecimal matchedAmount,
                            String contentScope, String status, LocalDateTime createTime,
                            Long userId, String userName, String payWay, String productSummary,
                            Integer productCount, Boolean hasDetails, Boolean difference,
                            Set<Long> matchedProducts) {
        ObjectNode node(ObjectMapper mapper) {
            ObjectNode n = mapper.createObjectNode();
            n.put("orderId", orderId); decimal(n, "amount", amount);
            decimal(n, "originalAmount", originalAmount);
            decimal(n, "visibleAmount", visibleAmount); decimal(n, "matchedAmount", matchedAmount);
            n.put("contentScope", contentScope); n.put("status", status);
            n.put("createTime", createTime.format(DATE_TIME)); n.put("userId", userId);
            text(n, "userName", userName); text(n, "payWay", payWay);
            n.put("productSummary", productSummary); n.put("productCount", productCount);
            n.put("hasDetails", hasDetails);
            if (difference == null) n.putNull("hasAmountDifference");
            else n.put("hasAmountDifference", difference);
            return n;
        }
    }

    private record Oracle(Scenario scenario, List<ApiOrder> pageOrders, int total,
                          BigDecimal totalSales, int orderCount, BigDecimal average,
                          int sold, int hot, int missing, Integer mismatch) {
        JsonNode a2(ObjectMapper mapper) {
            ObjectNode root = root(mapper), data = (ObjectNode) root.get("data");
            data.set("filters", filters(mapper)); data.set("scope", scope(mapper));
            text(data, "keyWord", scenario.keyword()); data.put("total", total);
            data.put("pageNum", scenario.page()); data.put("pageSize", scenario.size());
            ArrayNode list = data.putArray("orderList");
            pageOrders.forEach(order -> list.add(order.node(mapper)));
            return root;
        }
        JsonNode a3(ObjectMapper mapper) {
            ObjectNode root = root(mapper), data = (ObjectNode) root.get("data");
            data.set("filters", filters(mapper)); data.set("scope", scope(mapper));
            decimal(data, "totalSalesAmount", totalSales); data.put("uniqueOrderCount", orderCount);
            decimal(data, "averageOrderValue", average); data.put("soldProductCount", sold);
            data.put("hotProductCount", hot); data.put("missingDetailOrderCount", missing);
            if (mismatch == null) data.putNull("amountMismatchOrderCount");
            else data.put("amountMismatchOrderCount", mismatch);
            return root;
        }
        BigDecimal a2MatchedSum() {
            return pageOrders.stream().map(ApiOrder::matchedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        }
        private ObjectNode filters(ObjectMapper mapper) {
            ObjectNode n = mapper.createObjectNode(); n.put("startTime", scenario.start().toString());
            n.put("endTime", scenario.end().toString());
            ArrayNode categories = n.putArray("category"); scenario.categories().forEach(categories::add);
            ArrayNode brands = n.putArray("brand"); scenario.brands().forEach(brands::add);
            n.putArray("statuses").add("completed"); return n;
        }
        private ObjectNode scope(ObjectMapper mapper) {
            boolean admin = scenario.role() == Role.ADMIN;
            ObjectNode n = mapper.createObjectNode(); n.put("mode", admin ? "BRAND" : "ALL");
            ArrayNode allowed = n.putArray("allowedBrands");
            if (admin) allowed.add(scenario.accountBrand());
            n.put("canViewFullOrder", !admin); n.put("canManageUsers", !admin); return n;
        }
    }

    private static ObjectNode root(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode(); n.put("code", 200); n.put("message", "响应成功");
        n.set("data", mapper.createObjectNode()); return n;
    }
    private static void decimal(ObjectNode n, String key, BigDecimal value) {
        if (value == null) n.putNull(key); else n.put(key, value.stripTrailingZeros());
    }
    private static void text(ObjectNode n, String key, String value) {
        if (value == null) n.putNull(key); else n.put(key, value);
    }
}
