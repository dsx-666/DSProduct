package com.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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

/**
 * A3 销售 KPI 的可控数据测试和多线程性能测试。
 *
 * <p>生产代码没有为 GET 请求定义 JSON body。可复制的实际请求是：</p>
 * <pre>
 * GET /api/orders/kpi?startTime=2025-11-12&amp;endTime=2025-11-14&amp;statuses=completed
 * </pre>
 *
 * <p>如果调用方需要用 JSON 表示同一组筛选条件，其等价形式为：</p>
 * <pre>{@code
 * {
 *   "startTime": "2025-11-12",
 *   "endTime": "2025-11-14",
 *   "category": [],
 *   "brand": [],
 *   "statuses": ["completed"]
 * }
 * }</pre>
 */
@Disabled("已由 OrderA2A3BlackBoxDatabaseTest 的独立数据库黑盒测试替代")
class OrderKpiResponseTest {

    private static final String A3_FILTER_JSON = """
            {
              "startTime": "2025-11-12",
              "endTime": "2025-11-14",
              "category": [],
              "brand": [],
              "statuses": ["completed"]
            }
            """;

    private static final String A3_INJECTED_SUPER_ADMIN_RESULT_JSON = """
            {
              "code": 200,
              "message": "响应成功",
              "data": {
                "amountMismatchOrderCount": 1,
                "totalSalesAmount": 550.00,
                "uniqueOrderCount": 3,
                "averageOrderValue": 183.33,
                "missingDetailOrderCount": 1,
                "soldProductCount": null,
                "hotProductCount": null,
                "filters": {
                  "startTime": "2025-11-12",
                  "endTime": "2025-11-14",
                  "statuses": ["completed"],
                  "category": [],
                  "brand": []
                },
                "scope": null
              }
            }
            """;

    private ThreadPoolTaskExecutor executor;
    private MyParallelProcessor parallelProcessor;
    private OrderMapper orderMapper;
    private OrderServiceImpl orderService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("a3-test-");
        executor.initialize();

        parallelProcessor = new MyParallelProcessor(executor);
        orderMapper = mock(OrderMapper.class);
        orderService = new OrderServiceImpl(
                orderMapper,
                mock(FilterValidator.class),
                parallelProcessor
        );
        objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void superAdminInjectedData_returnsCurrentA3Json() throws Exception {
        Filter filter = completedFilter();
        when(orderMapper.selectOrderKpi(filter)).thenReturn(List.of(
                order("200.00", "200.00"),
                order("100.00", "90.00"),
                order("250.00", null)
        ));

        OrderController controller = new OrderController(orderService);
        Result<OrderKpiVo> response = controller.selectOrderKpiInfo(
                details(Role.SUPER_ADMIN, null),
                filter
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTotalSalesAmount())
                .isEqualByComparingTo("550.00");
        assertThat(response.getData().getUniqueOrderCount()).isEqualTo(3L);
        assertThat(response.getData().getAverageOrderValue())
                .isEqualByComparingTo("183.33");
        assertThat(response.getData().getMissingDetailOrderCount()).isEqualTo(1L);
        assertThat(response.getData().getAmountMismatchOrderCount()).isEqualTo(1L);

        // 当前实现尚未为下面三个字段赋值；这个断言用于准确记录实际 JSON。
        assertThat(response.getData().getSoldProductCount()).isNull();
        assertThat(response.getData().getHotProductCount()).isNull();
        assertThat(response.getData().getScope()).isNull();

        System.out.println("A3_FILTER_JSON=" + A3_FILTER_JSON);
        System.out.println("A3_EXPECTED_JSON=" + A3_INJECTED_SUPER_ADMIN_RESULT_JSON);
        printJson("A3_INJECTED_SUPER_ADMIN_RESPONSE", response);
    }

    @Test
    void adminInjectedData_hidesWholeOrderAuditFields() throws Exception {
        Filter filter = completedFilter();
        when(orderMapper.selectOrderKpi(filter)).thenReturn(List.of(
                order("200.00", "200.00"),
                order("100.00", "90.00"),
                order("250.00", null)
        ));

        Result<OrderKpiVo> response = new OrderController(orderService)
                .selectOrderKpiInfo(details(Role.ADMIN, "NeoTech"), filter);

        assertThat(response.getData().getTotalSalesAmount())
                .isEqualByComparingTo("550.00");
        assertThat(response.getData().getMissingDetailOrderCount()).isZero();
        assertThat(response.getData().getAmountMismatchOrderCount()).isNull();

        printJson("A3_INJECTED_ADMIN_RESPONSE", response);
    }

    @Test
    void emptyInjectedData_documentsCurrentEmptyResult() throws Exception {
        Filter filter = completedFilter();
        when(orderMapper.selectOrderKpi(filter)).thenReturn(List.of());

        Result<OrderKpiVo> response = new OrderController(orderService)
                .selectOrderKpiInfo(details(Role.SUPER_ADMIN, null), filter);

        assertThat(response.getData().getTotalSalesAmount())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getData().getUniqueOrderCount()).isZero();
        // 需求期望是 0，当前实现返回 null；保留断言以防测试报告掩盖现状。
        assertThat(response.getData().getAverageOrderValue()).isNull();

        printJson("A3_INJECTED_EMPTY_RESPONSE", response);
    }

    @Test
    void oneWorkerVersusFourWorkers_reportsMeasuredSpeedupAndSameResult() throws Exception {
        Map<String, Object> normal = order("123.45", "123.45");
        Map<String, Object> mismatch = order("100.00", "99.00");
        Map<String, Object> missing = order("80.00", null);
        List<Map<String, Object>> source = new ArrayList<>(300_000);
        for (int i = 0; i < 100_000; i++) {
            source.add(normal);
            source.add(mismatch);
            source.add(missing);
        }

        Function<List<Map<String, Object>>, OrderKpiResponse> processor = part ->
                ReflectionTestUtils.invokeMethod(orderService, "processKpi", part);
        BiFunction<OrderKpiResponse, OrderKpiResponse, OrderKpiResponse> merger = (a, b) ->
                ReflectionTestUtils.invokeMethod(orderService, "mergerKpi", a, b);

        // 预热 JIT，避免把首次类加载时间误算为单线程耗时。
        List<Map<String, Object>> warmup = source.subList(0, 30_000);
        parallelProcessor.processInParallel(warmup, 1, processor, emptyKpi(), merger);
        parallelProcessor.processInParallel(warmup, 4, processor, emptyKpi(), merger);

        long[] oneWorkerNanos = new long[5];
        long[] fourWorkerNanos = new long[5];
        OrderKpiResponse oneWorkerResult = null;
        OrderKpiResponse fourWorkerResult = null;

        for (int round = 0; round < 5; round++) {
            TimedResult first;
            TimedResult second;
            if (round % 2 == 0) {
                first = measure(source, 1, processor, merger);
                second = measure(source, 4, processor, merger);
                oneWorkerNanos[round] = first.nanos();
                fourWorkerNanos[round] = second.nanos();
                oneWorkerResult = first.result();
                fourWorkerResult = second.result();
            } else {
                first = measure(source, 4, processor, merger);
                second = measure(source, 1, processor, merger);
                fourWorkerNanos[round] = first.nanos();
                oneWorkerNanos[round] = second.nanos();
                fourWorkerResult = first.result();
                oneWorkerResult = second.result();
            }
        }

        assertSameKpi(oneWorkerResult, fourWorkerResult);

        double oneWorkerMs = nanosToMillis(median(oneWorkerNanos));
        double fourWorkerMs = nanosToMillis(median(fourWorkerNanos));
        double speedup = oneWorkerMs / fourWorkerMs;
        double improvementPercent = (oneWorkerMs - fourWorkerMs) / oneWorkerMs * 100.0;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dataSize", source.size());
        report.put("rounds", 5);
        report.put("oneWorkerMedianMs", round(oneWorkerMs));
        report.put("fourWorkerMedianMs", round(fourWorkerMs));
        report.put("speedupTimes", round(speedup));
        report.put("timeReductionPercent", round(improvementPercent));
        report.put("note", "仅测数据库返回后的 Java KPI 聚合，不包含 MySQL 查询时间");
        printJson("A3_PARALLEL_PERFORMANCE", report);
    }

    private TimedResult measure(
            List<Map<String, Object>> source,
            int workers,
            Function<List<Map<String, Object>>, OrderKpiResponse> processor,
            BiFunction<OrderKpiResponse, OrderKpiResponse, OrderKpiResponse> merger
    ) throws Exception {
        long start = System.nanoTime();
        OrderKpiResponse result = parallelProcessor.processInParallel(
                source, workers, processor, emptyKpi(), merger
        );
        return new TimedResult(System.nanoTime() - start, result);
    }

    private static void assertSameKpi(OrderKpiResponse left, OrderKpiResponse right) {
        assertThat(right.getTotalSalesAmount())
                .isEqualByComparingTo(left.getTotalSalesAmount());
        assertThat(right.getUniqueOrderCount()).isEqualTo(left.getUniqueOrderCount());
        assertThat(right.getAverageOrderValue())
                .isEqualByComparingTo(left.getAverageOrderValue());
        assertThat(right.getMissingDetailOrderCount())
                .isEqualTo(left.getMissingDetailOrderCount());
        assertThat(right.getAmountMismatchOrderCount())
                .isEqualTo(left.getAmountMismatchOrderCount());
    }

    private static OrderKpiResponse emptyKpi() {
        return OrderKpiResponse.builder()
                .amountMismatchOrderCount(0L)
                .totalSalesAmount(BigDecimal.ZERO)
                .uniqueOrderCount(0L)
                .missingDetailOrderCount(0L)
                .build();
    }

    private static Filter completedFilter() {
        return Filter.builder()
                .startTime(LocalDate.of(2025, 11, 12))
                .endTime(LocalDate.of(2025, 11, 14))
                .categories(List.of())
                .brands(List.of())
                .statuses(List.of("completed"))
                .build();
    }

    private static Map<String, Object> order(String amount, String matchedAmount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", new BigDecimal(amount));
        row.put("matchedAmount", matchedAmount == null ? null : new BigDecimal(matchedAmount));
        return row;
    }

    private static MyUserDetails details(Role role, String brand) {
        User user = User.builder()
                .userName("a3-test-user")
                .password("not-used")
                .role(role)
                .brand(brand)
                .build();
        return MyUserDetails.builder()
                .username(user.getUserName())
                .password(user.getPassword())
                .user(user)
                .build();
    }

    private void printJson(String label, Object value) throws Exception {
        System.out.println(label + "="
                + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
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

    private record TimedResult(long nanos, OrderKpiResponse result) {
    }
}
