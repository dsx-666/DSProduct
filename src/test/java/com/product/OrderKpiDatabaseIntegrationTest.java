package com.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.product.controller.OrderController;
import com.product.enums.Role;
import com.product.mapper.OrderMapper;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.po.User;
import com.product.pojo.vo.OrderKpiVo;
import com.product.pojo.vo.Result;
import com.product.service.impl.FilterValidator;
import com.product.service.impl.OrderServiceImpl;
import com.product.thread.MyParallelProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用 application.yml 中 DSProduct MySQL 的只读 A3 集成测试。
 * 不执行 INSERT、UPDATE、DELETE 或 DDL。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Disabled("已由 OrderA2A3BlackBoxDatabaseTest 的独立数据库黑盒测试替代")
class OrderKpiDatabaseIntegrationTest {

    private static final String REAL_DATABASE_SQL = """
            SELECT SUM(oi.item_total) AS matchedAmount,
                   o.total_amount AS amount
            FROM `order` o
            LEFT JOIN order_item oi ON o.order_id = oi.order_id
            WHERE o.order_date >= ?
              AND o.order_date < DATE_ADD(?, INTERVAL 1 DAY)
              AND o.order_status = 'completed'
            GROUP BY o.order_id
            ORDER BY o.order_date DESC, o.order_id ASC
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MyParallelProcessor parallelProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void realDatabaseRowsInjectedIntoParallelService_returnsCalculatedJson() throws Exception {
        Filter filter = Filter.builder()
                .startTime(LocalDate.of(2024, 1, 1))
                .endTime(LocalDate.of(2025, 11, 14))
                .categories(List.of())
                .brands(List.of())
                .statuses(List.of("completed"))
                .build();
        // 先预热连接池，再取 5 次真实 SQL 中位数，避免把首次建连时间混入结果。
        queryRealRows(filter);
        long[] queryNanos = new long[5];
        List<Map<String, Object>> databaseRows = List.of();
        for (int i = 0; i < queryNanos.length; i++) {
            long start = System.nanoTime();
            databaseRows = queryRealRows(filter);
            queryNanos[i] = System.nanoTime() - start;
        }
        ExpectedKpi expected = calculateExpected(databaseRows);

        OrderMapper injectedMapper = mock(OrderMapper.class);
        when(injectedMapper.selectOrderKpi(filter)).thenReturn(databaseRows);
        OrderServiceImpl service = new OrderServiceImpl(
                injectedMapper,
                mock(FilterValidator.class),
                parallelProcessor
        );
        Result<OrderKpiVo> response = new OrderController(service)
                .selectOrderKpiInfo(superAdmin(), filter);
        JsonNode data = objectMapper.valueToTree(response).path("data");

        assertThat(data.path("totalSalesAmount").decimalValue())
                .isEqualByComparingTo(expected.totalSalesAmount());
        assertThat(data.path("uniqueOrderCount").longValue())
                .isEqualTo(expected.uniqueOrderCount());
        assertThat(data.path("averageOrderValue").decimalValue())
                .isEqualByComparingTo(expected.averageOrderValue());
        assertThat(data.path("missingDetailOrderCount").longValue())
                .isEqualTo(expected.missingDetailOrderCount());
        assertThat(data.path("amountMismatchOrderCount").longValue())
                .isEqualTo(expected.amountMismatchOrderCount());

        Map<String, Object> performance = measureRealRows(
                service, databaseRows, median(queryNanos)
        );

        System.out.println("A3_DATABASE_REQUEST="
                + "GET /api/orders/kpi?startTime=2024-01-01&endTime=2025-11-14&statuses=completed");
        System.out.println("A3_DATABASE_INJECTED_ROW_COUNT=" + databaseRows.size());
        System.out.println("A3_DATABASE_PERFORMANCE="
                + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(performance));
        System.out.println("A3_DATABASE_RESPONSE="
                + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
    }

    @Test
    void documentedA3HttpPath_currentlyReturnsBusinessErrorBecauseMapperReadsMissingKeyWord() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/orders/kpi")
                        .param("startTime", "2024-01-01")
                        .param("endTime", "2025-11-14")
                        .param("statuses", "completed")
                        .with(user(superAdmin())))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = new String(
                mvcResult.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8
        );
        JsonNode root = objectMapper.readTree(responseJson);
        assertThat(root.path("code").intValue()).isEqualTo(500);
        assertThat(root.path("data").isNull()).isTrue();

        System.out.println("A3_CURRENT_HTTP_FAILURE="
                + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private static ExpectedKpi calculateExpected(List<Map<String, Object>> rows) {
        BigDecimal total = BigDecimal.ZERO;
        long missing = 0;
        long mismatch = 0;

        for (Map<String, Object> row : rows) {
            BigDecimal amount = new BigDecimal(row.get("amount").toString())
                    .setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount).setScale(2, RoundingMode.HALF_UP);
            Object matchedValue = row.get("matchedAmount");
            if (matchedValue == null) {
                missing++;
            } else {
                BigDecimal matched = new BigDecimal(matchedValue.toString())
                        .setScale(2, RoundingMode.HALF_UP);
                if (amount.subtract(matched).abs()
                        .compareTo(new BigDecimal("0.01")) > 0) {
                    mismatch++;
                }
            }
        }

        long count = rows.size();
        BigDecimal average = count == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new ExpectedKpi(total, count, average, missing, mismatch);
    }

    private List<Map<String, Object>> queryRealRows(Filter filter) {
        return jdbcTemplate.queryForList(
                REAL_DATABASE_SQL,
                filter.getStartTime(),
                filter.getEndTime()
        );
    }

    private Map<String, Object> measureRealRows(
            OrderServiceImpl service,
            List<Map<String, Object>> rows,
            long queryMedianNanos
    ) throws Exception {
        Function<List<Map<String, Object>>, OrderKpiResponse> processor = part ->
                ReflectionTestUtils.invokeMethod(service, "processKpi", part);
        BiFunction<OrderKpiResponse, OrderKpiResponse, OrderKpiResponse> merger = (a, b) ->
                ReflectionTestUtils.invokeMethod(service, "mergerKpi", a, b);

        parallelProcessor.processInParallel(rows, 1, processor, emptyKpi(), merger);
        parallelProcessor.processInParallel(rows, 4, processor, emptyKpi(), merger);

        long[] oneWorkerNanos = new long[11];
        long[] fourWorkerNanos = new long[11];
        for (int i = 0; i < oneWorkerNanos.length; i++) {
            if (i % 2 == 0) {
                oneWorkerNanos[i] = measureAggregation(rows, 1, processor, merger);
                fourWorkerNanos[i] = measureAggregation(rows, 4, processor, merger);
            } else {
                fourWorkerNanos[i] = measureAggregation(rows, 4, processor, merger);
                oneWorkerNanos[i] = measureAggregation(rows, 1, processor, merger);
            }
        }

        double queryMs = nanosToMillis(queryMedianNanos);
        double oneMs = nanosToMillis(median(oneWorkerNanos));
        double fourMs = nanosToMillis(median(fourWorkerNanos));
        double aggregationReduction = (oneMs - fourMs) / oneMs * 100.0;
        double endToEndReduction = ((queryMs + oneMs) - (queryMs + fourMs))
                / (queryMs + oneMs) * 100.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseRowCount", rows.size());
        result.put("queryMedianMs", round(queryMs));
        result.put("oneWorkerAggregationMedianMs", round(oneMs));
        result.put("fourWorkerAggregationMedianMs", round(fourMs));
        result.put("aggregationSpeedupTimes", round(oneMs / fourMs));
        result.put("aggregationTimeReductionPercent", round(aggregationReduction));
        result.put("estimatedEndToEndTimeReductionPercent", round(endToEndReduction));
        return result;
    }

    private long measureAggregation(
            List<Map<String, Object>> rows,
            int workers,
            Function<List<Map<String, Object>>, OrderKpiResponse> processor,
            BiFunction<OrderKpiResponse, OrderKpiResponse, OrderKpiResponse> merger
    ) throws Exception {
        long start = System.nanoTime();
        parallelProcessor.processInParallel(rows, workers, processor, emptyKpi(), merger);
        return System.nanoTime() - start;
    }

    private static OrderKpiResponse emptyKpi() {
        return OrderKpiResponse.builder()
                .amountMismatchOrderCount(0L)
                .totalSalesAmount(BigDecimal.ZERO)
                .uniqueOrderCount(0L)
                .missingDetailOrderCount(0L)
                .build();
    }

    private static long median(long[] values) {
        long[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    private static double nanosToMillis(long nanos) {
        return nanos / (double) TimeUnit.MILLISECONDS.toNanos(1);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static MyUserDetails superAdmin() {
        User user = User.builder()
                .userName("a3-db-test")
                .password("not-used")
                .role(Role.SUPER_ADMIN)
                .build();
        return MyUserDetails.builder()
                .username(user.getUserName())
                .password(user.getPassword())
                .user(user)
                .build();
    }

    private record ExpectedKpi(
            BigDecimal totalSalesAmount,
            long uniqueOrderCount,
            BigDecimal averageOrderValue,
            long missingDetailOrderCount,
            long amountMismatchOrderCount
    ) {
    }
}
