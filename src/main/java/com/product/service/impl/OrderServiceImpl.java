package com.product.service.impl;
import com.product.MyException.BusinessException;
import com.product.converter.UserConverter;
import com.product.enums.Code;
import com.product.enums.Role;
import com.product.mapper.OrderMapper;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.dto.response.OrderListResponse;
import com.product.pojo.bo.QueryOrderListResult;
import com.product.pojo.po.User;
import com.product.pojo.bo.Scope;
import com.product.service.OrderService;
import com.product.service.ProductService;
import com.product.thread.MyParallelProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final FilterValidator filterValidator;
    private final MyParallelProcessor parallelProcessor;
    private final ProductService productService;

    // 接口A2获取order数据
    @Override
    public OrderListResponse selectOrderInfo(
            OrderListRequest orderListRequest,
            User user) {
        // 参数业务校验(防止后续的缓存击穿)
        filterValidator.validate(orderListRequest.getFilters());
        Scope scope = UserConverter.toScope(user);
        scope.validateFilter(orderListRequest.getFilters());
        /*
        TODO: 以后项目大了可以尝试转成Bo进行业务逻辑校验，这个OrderBo也可以加入ProductBo
         并且可以再封装一个方法调用属性的方法省的去.属性.方法
        */
        // 操作数据库
        orderListRequest.setOffset((orderListRequest.getPage()-1)*orderListRequest.getNum());
        List<QueryOrderListResult> list = orderMapper.selectOrderList(orderListRequest);
        List<Long> listIds = list
                .stream()
                .map(QueryOrderListResult::getOrderId)
                .toList();

        Long count = orderMapper.selectOrderListCount(orderListRequest);



        List<BigDecimal> listOriginalAmount =
                orderMapper.selectOrderOriginalAmount(listIds);

        List<BigDecimal> listVisibleAmountByBrand =
                orderMapper.selectVisibleAmountByBrand(scope, listIds);
        List<QueryOrderListResult> result = IntStream.range(0,list.size())
                .mapToObj(i -> list.get(i).ToList(
                        scope,
                        listOriginalAmount.get(i),
                        listVisibleAmountByBrand.get(i))
                )
                .toList();
        return OrderListResponse.builder()
                .scope(scope)
                .orderList(result)
                .total(count)
                .build();
        // 业务逻辑
    }

    // TODO:采用多线程
    @Override
    public OrderKpiResponse selectOrderKpi(Filter filter,User user) throws
            ExecutionException,
            InterruptedException {
        log.info("正在进行参数验证");
        filterValidator.validate(filter);
        log.info("参数验证完成");
        Scope scope = UserConverter.toScope(user);
        log.info("正在进行权限验证");
        if(!scope.validateFilter(filter)) {
            throw new BusinessException(Code.AccessDeniedError.getCode(),
                    Code.AccessDeniedError.getDesc());
        }
        log.info("权限验证完成");

        if("BRAND".equals(scope.getMode()) && scope.getAllowedBrands().isEmpty()){
            return OrderKpiResponse.builder()
                    .amountMismatchOrderCount(null)
                    .totalSalesAmount(BigDecimal.ZERO.setScale(2,RoundingMode.HALF_UP))
                    .uniqueOrderCount((long) 0)
                    .averageOrderValue(BigDecimal.ZERO.setScale(2,RoundingMode.HALF_UP))
                    .missingDetailOrderCount((long) 0)
                    .soldProductCount((long) 0)
                    .hotProductCount((long) 0)
                    .scope(scope)
                    .build();
        }

        Filter queryFilter = filter;
        if("BRAND".equals(scope.getMode()) && filter.getBrands().isEmpty()){
            queryFilter = Filter.builder()
                    .startTime(filter.getStartTime())
                    .endTime(filter.getEndTime())
                    .categories(filter.getCategories())
                    .brands(scope.getAllowedBrands())
                    .statuses(filter.getStatuses())
                    .build();
        }

        // 价格等kpi数据处理

        List<Map<String,Object>> list = orderMapper.selectOrderKpi(queryFilter);
        List<Long> orderIds = list.stream()
                .map(map -> ((BigInteger) map.get("orderId"))
                        .longValue())
                .toList();
        // 获取order_item的价格总和
        List<BigDecimal> originalAmount = orderMapper.selectOrderOriginalAmount(orderIds);
        IntStream.range(0, list.size())
                .forEach(i ->
                        list.get(i).put("originalAmount", originalAmount.get(i))
                );
        OrderKpiResponse result = OrderKpiResponse.builder()
                .amountMismatchOrderCount((long) 0)
                .totalSalesAmount(BigDecimal.ZERO)
                .uniqueOrderCount((long) 0)
                .missingDetailOrderCount((long) 0)
                .averageOrderValue(BigDecimal.ZERO)
                .scope(scope)
                .build();

        result =  parallelProcessor
                .processInParallel(list,4,this::processKpi,result,this::mergerKpi);
        // 操作redis
        List<Map<String,Long>> productCount = productService.getProductCount(scope);
        // 获取productId
        List<Object> productId = productService.selectProductIdByFilter(queryFilter);
        // 计算排名前 30% 的商品数量
        int topCount = (int) Math.ceil(productCount.size() * 0.3);
        // 获取排名前 30% 的商品 ID
        Set<Long> topProductIds = productCount.stream()
                .limit(topCount)
                .map(item -> item.get("productId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 统计 productId 命中前 30% 的数量
        long hitCount = productId.stream()
                .filter(Objects::nonNull)
                .map(id -> Long.valueOf(id.toString()))
                .filter(topProductIds::contains)
                .count();
        result.setHotProductCount(hitCount);
        result.setSoldProductCount((long) productId.size());
        // 权限不够
        if(scope.getMode().equals("BRAND")){
            result.setMissingDetailOrderCount((long) 0);
            result.setAmountMismatchOrderCount(null);
        }

        return result;
    }

    @Override
    public OrderKpiResponse.OrderStatsResponse selectOrderStats(
            Filter filter,
            String granularity,
            User user) {
        if(user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN){
            throw new BusinessException(Code.AccessDeniedError.getCode(),
                    Code.AccessDeniedError.getDesc());
        }

        if(granularity == null
                || !List.of("day","week","month").contains(granularity)){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":granularity只支持day、week或month");
        }

        filter.setStatuses(List.of("completed"));
        filterValidator.validate(filter);
        Scope scope = UserConverter.toScope(user);
        if(!scope.validateFilter(filter)){
            throw new BusinessException(Code.AccessDeniedError.getCode(),
                    Code.AccessDeniedError.getDesc());
        }

        List<LocalDate> dateList = new ArrayList<>();
        List<LocalDate> periodStartList = new ArrayList<>();
        List<LocalDate> periodEndList = new ArrayList<>();
        List<BigDecimal> salesAmountList = new ArrayList<>();
        List<Long> orderCountList = new ArrayList<>();
        Map<LocalDate,Integer> periodIndex = new HashMap<>();

        LocalDate periodDate = getPeriodStart(filter.getStartTime(),granularity);
        while(!periodDate.isAfter(filter.getEndTime())){
            LocalDate periodEnd = getPeriodEnd(periodDate,granularity);
            dateList.add(periodDate);
            periodStartList.add(periodDate.isBefore(filter.getStartTime())
                    ? filter.getStartTime()
                    : periodDate);
            periodEndList.add(periodEnd.isAfter(filter.getEndTime())
                    ? filter.getEndTime()
                    : periodEnd);
            salesAmountList.add(BigDecimal.ZERO.setScale(2,RoundingMode.HALF_UP));
            orderCountList.add((long) 0);
            periodIndex.put(periodDate,dateList.size()-1);
            periodDate = getNextPeriod(periodDate,granularity);
        }

        List<Map<String,Object>> orderStats = List.of();
        if(!"BRAND".equals(scope.getMode()) || !scope.getAllowedBrands().isEmpty()){
            orderStats = orderMapper.selectOrderStats(filter,scope);
        }

        for(Map<String,Object> item : orderStats){
            LocalDate orderDate = toLocalDate(item.get("orderDate"));
            LocalDate orderPeriod = getPeriodStart(orderDate,granularity);
            Integer index = periodIndex.get(orderPeriod);
            if(index == null){
                continue;
            }

            BigDecimal salesAmount = new BigDecimal(item.get("salesAmount").toString())
                    .setScale(2,RoundingMode.HALF_UP);
            salesAmountList.set(index,salesAmountList.get(index)
                    .add(salesAmount)
                    .setScale(2,RoundingMode.HALF_UP));
            orderCountList.set(index,orderCountList.get(index)+1);
        }

        return OrderKpiResponse.OrderStatsResponse.builder()
                .scope(scope)
                .dateList(dateList)
                .periodStartList(periodStartList)
                .periodEndList(periodEndList)
                .salesAmountList(salesAmountList)
                .orderCountList(orderCountList)
                .build();
    }

    private LocalDate getPeriodStart(LocalDate date,String granularity){
        return switch (granularity){
            case "week" -> date.with(TemporalAdjusters
                    .previousOrSame(DayOfWeek.MONDAY));
            case "month" -> date.withDayOfMonth(1);
            default -> date;
        };
    }

    private LocalDate getPeriodEnd(LocalDate date,String granularity){
        return switch (granularity){
            case "week" -> date.plusDays(6);
            case "month" -> date.with(TemporalAdjusters.lastDayOfMonth());
            default -> date;
        };
    }

    private LocalDate getNextPeriod(LocalDate date,String granularity){
        return switch (granularity){
            case "week" -> date.plusWeeks(1);
            case "month" -> date.plusMonths(1);
            default -> date.plusDays(1);
        };
    }

    private LocalDate toLocalDate(Object date){
        if(date instanceof LocalDate localDate){
            return localDate;
        }
        if(date instanceof Date sqlDate){
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(date.toString());
    }










    // 获取数据库后对数据库操作得到对应的kpi
    // 实现
    private OrderKpiResponse processKpi(List<Map<String,Object>> list){
        OrderKpiResponse temp = OrderKpiResponse.builder()
                .amountMismatchOrderCount((long) 0)
                .totalSalesAmount(BigDecimal.ZERO)
                .uniqueOrderCount((long) 0)
                .missingDetailOrderCount((long) 0)
                .build();
        for(Map<String,Object> map : list){
            // 订单总数
            temp.setUniqueOrderCount(temp.getUniqueOrderCount() + 1);
            // 用于计算差额订单
            BigDecimal amount = new BigDecimal(map.get("amount").toString())
                    .setScale(2, RoundingMode.HALF_UP);

            if(map.get("matchedAmount")==null){
                // 缺明细订单
                temp.setMissingDetailOrderCount(temp.getMissingDetailOrderCount() + 1);
            }else{
                // 用于计算总金额
                BigDecimal matchedAmount = new BigDecimal(map.get("matchedAmount").toString())
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal OriginalAmount = new BigDecimal(map.get("originalAmount").toString())
                        .setScale(2, RoundingMode.HALF_UP);
                // 总金额
                temp.setTotalSalesAmount(temp.getTotalSalesAmount()
                        .add(matchedAmount)
                        .setScale(2, RoundingMode.HALF_UP));
                // 差额订单
                BigDecimal difference = amount.subtract(OriginalAmount).abs();
                if (difference.compareTo(new BigDecimal("0.01")) > 0) {
                    temp.setAmountMismatchOrderCount(temp.getAmountMismatchOrderCount() + 1);
                }
            }
        }
        // 求averageOrderValue
        temp.setAverageOrderValue(temp.getTotalSalesAmount().divide(
                BigDecimal.valueOf(temp.getUniqueOrderCount()),
                2,
                RoundingMode.HALF_UP));
        return temp;
    }

    private OrderKpiResponse mergerKpi(
            OrderKpiResponse a,
            OrderKpiResponse b
    ) {
        a.setMissingDetailOrderCount(
                a.getMissingDetailOrderCount()
                        + b.getMissingDetailOrderCount()
        );

        a.setAmountMismatchOrderCount(
                a.getAmountMismatchOrderCount()
                        + b.getAmountMismatchOrderCount()
        );

        // 合并订单数
        a.setUniqueOrderCount(
                a.getUniqueOrderCount()
                        + b.getUniqueOrderCount()
        );

        // 合并销售总额
        a.setTotalSalesAmount(
                a.getTotalSalesAmount()
                        .add(b.getTotalSalesAmount())
                        .setScale(2, RoundingMode.HALF_UP)
        );

        // 计算合并后的平均客单价
        if (a.getUniqueOrderCount() > 0) {
            a.setAverageOrderValue(
                    a.getTotalSalesAmount().divide(
                            BigDecimal.valueOf(a.getUniqueOrderCount()),
                            2,
                            RoundingMode.HALF_UP
                    )
            );
        } else {
            a.setAverageOrderValue(
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            );
        }

        return a;
    }

}
