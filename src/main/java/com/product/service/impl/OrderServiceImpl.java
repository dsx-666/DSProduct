package com.product.service.impl;
import com.product.MyException.BusinessException;
import com.product.MyException.ApiHttpException;
import com.product.converter.UserConverter;
import com.product.enums.Code;
import com.product.enums.ProductCategory;
import com.product.enums.Role;
import com.product.mapper.OrderMapper;
import com.product.pojo.bo.OrderDetailItem;
import com.product.pojo.bo.OrderPaymentStatsList;
import com.product.pojo.bo.MyOrderListItem;
import com.product.pojo.bo.MyOrderListRow;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.request.MyOrderListRequest;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.dto.response.OrderListResponse;
import com.product.pojo.dto.response.OrderDetailResponse;
import com.product.pojo.dto.response.OrderPaymentStatsResponse;
import com.product.pojo.dto.response.MyOrderListResponse;
import com.product.pojo.bo.QueryOrderListResult;
import com.product.pojo.po.User;
import com.product.pojo.bo.Scope;
import com.product.service.OrderService;
import com.product.service.ProductService;
import com.product.thread.MyParallelProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2025,11,14);
    private static final LocalDate BUSINESS_START_DATE = LocalDate.of(2024,1,1);
    private static final List<String> ALL_ORDER_STATUSES = List.of(
            "processing","shipped","completed","cancelled","returned"
    );
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

    @Override
    public OrderKpiResponse selectOrderKpi(Filter filter,User user) throws
            ExecutionException,
            InterruptedException {
        filter.setStatuses(List.of("completed"));
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
            // 这个是为了求映射到的index
            periodIndex.put(periodDate,dateList.size()-1);
            periodDate = getNextPeriod(periodDate,granularity);
        }

        List<Map<String,Object>> orderStats = List.of();
        orderStats = orderMapper.selectOrderStats(filter,scope);

        for(Map<String,Object> item : orderStats){
            LocalDate orderDate = toLocalDate(item.get("orderDate"));
            LocalDate orderPeriod = getPeriodStart(orderDate,granularity);
            Integer index = periodIndex.get(orderPeriod);
            if(index == null){
                continue;
            }
            // 因为获取范围内最前面的日期，可以通过之前的索引来对应，去到和新的再相加
            BigDecimal salesAmount = new BigDecimal(item.get("salesAmount").toString())
                    .setScale(2,RoundingMode.HALF_UP);
            salesAmountList.set(index,salesAmountList.get(index)
                    .add(salesAmount)
                    .setScale(2,RoundingMode.HALF_UP));
            // 因为是根据orderId进行分组的，而这个是求订单数所以加1
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

    @Override
    public OrderPaymentStatsResponse selectOrderPaymentStats(Filter filter,User user) {
        filter.setStatuses(List.of("completed"));
        filterValidator.validate(filter);

        Scope scope = UserConverter.toScope(user);
        if(!scope.validateFilter(filter)){
            throw new BusinessException(Code.AccessDeniedError.getCode(),
                    Code.AccessDeniedError.getDesc());
        }

        List<OrderPaymentStatsList> payMethodList = orderMapper
                .selectOrderPaymentStats(filter,scope)
                .stream()
                .map(this::toOrderPaymentStatsList)
                .sorted(Comparator.comparingInt(item ->
                        getPayWayOrder(item.getPayWay())))
                .toList();

        long totalOrderCount = payMethodList.stream()
                .mapToLong(OrderPaymentStatsList::getOrderCount)
                .sum();

        for(OrderPaymentStatsList item : payMethodList){
            item.setPayRatio(getRatio(item.getOrderCount(),totalOrderCount));
        }

        return OrderPaymentStatsResponse.builder()
                .scope(scope)
                .totalOrderCount(totalOrderCount)
                .payMethodList(payMethodList)
                .build();
    }

    @Override
    public OrderDetailResponse selectOrderDetail(Long orderId,User user) {
        if(orderId == null || orderId <= 0){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":orderId必须为正整数");
        }

        Scope scope = UserConverter.toScope(user);
        Map<String,Object> orderDetail = orderMapper.selectOrderDetail(orderId);
        if(orderDetail == null){
            throw new BusinessException(404,"订单不存在");
        }

        List<String> brands = "BRAND".equals(scope.getMode())
                ? scope.getAllowedBrands()
                : List.of();
        if("BRAND".equals(scope.getMode()) && brands.isEmpty()){
            throw new BusinessException(404,"订单不存在");
        }

        List<OrderDetailItem> itemList = orderMapper
                .selectOrderDetailItemList(orderId,brands)
                .stream()
                .map(this::toOrderDetailItem)
                .toList();

        if("BRAND".equals(scope.getMode()) && itemList.isEmpty()){
            throw new BusinessException(404,"订单不存在");
        }

        BigDecimal originalAmount = toAmount(orderDetail.get("originalAmount"));
        BigDecimal itemsTotalAmount = itemList.isEmpty()
                ? null
                : itemList.stream()
                .map(OrderDetailItem::getItemTotal)
                .reduce(BigDecimal.ZERO,(a,b) -> a.add(b)
                        .setScale(2,RoundingMode.HALF_UP));
        Long visibleQuantity = itemList.isEmpty()
                ? null
                : itemList.stream()
                .mapToLong(OrderDetailItem::getQuantity)
                .sum();

        OrderDetailResponse.OrderDetailResponseBuilder result = OrderDetailResponse
                .builder()
                .orderId(((Number) orderDetail.get("orderId")).longValue())
                .status(orderDetail.get("status").toString())
                .createTime(toLocalDateTime(orderDetail.get("createTime")))
                .userId(((Number) orderDetail.get("userId")).longValue())
                .userName(orderDetail.get("userName") == null
                        ? null
                        : orderDetail.get("userName").toString())
                .payWay(orderDetail.get("payWay") == null
                        ? null
                        : orderDetail.get("payWay").toString())
                .hasDetails(!itemList.isEmpty())
                .itemCount((long) itemList.size())
                .items(itemList);

        if("BRAND".equals(scope.getMode())){
            return result
                    .contentScope("BRAND")
                    .amount(null)
                    .originalAmount(null)
                    .itemsTotalAmount(null)
                    .amountDifference(null)
                    .hasAmountDifference(null)
                    .visibleAmount(itemsTotalAmount)
                    .visibleQuantity(visibleQuantity)
                    .totalQuantity(null)
                    .amountSource("BRAND_ITEMS")
                    .build();
        }

        if(itemList.isEmpty()){
            return result
                    .contentScope("FULL")
                    .amount(originalAmount)
                    .originalAmount(originalAmount)
                    .itemsTotalAmount(null)
                    .amountDifference(null)
                    .hasAmountDifference(null)
                    .visibleAmount(originalAmount)
                    .visibleQuantity(null)
                    .totalQuantity(null)
                    .amountSource("ORDER_TOTAL")
                    .build();
        }

        BigDecimal amountDifference = itemsTotalAmount
                .subtract(originalAmount)
                .setScale(2,RoundingMode.HALF_UP);
        return result
                .contentScope("FULL")
                .amount(itemsTotalAmount)
                .originalAmount(originalAmount)
                .itemsTotalAmount(itemsTotalAmount)
                .amountDifference(amountDifference)
                .hasAmountDifference(amountDifference.abs()
                        .compareTo(new BigDecimal("0.01")) > 0)
                .visibleAmount(itemsTotalAmount)
                .visibleQuantity(visibleQuantity)
                .totalQuantity(visibleQuantity)
                .amountSource("ITEM_SUM")
                .build();
    }

    @Override
    public MyOrderListResponse selectMyOrderList(
            MyOrderListRequest request,
            User user) {
        if(user == null || user.getRole() != Role.NORMAL_USER){
            throw new ApiHttpException(HttpStatus.FORBIDDEN,403,"无权访问");
        }
        validateMyOrderListRequest(request);

        List<MyOrderListItem> orderList = orderMapper
                .selectMyOrderList(request,user.getUserId())
                .stream()
                .map(this::toMyOrderListItem)
                .toList();
        Filter filters = Filter.builder()
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .categories(List.of())
                .brands(List.of())
                .statuses(request.getStatuses())
                .build();

        return MyOrderListResponse.builder()
                .filters(filters)
                .scope(UserConverter.toScope(user))
                .keyWord(request.getKeyWord())
                .total(orderMapper.selectMyOrderListCount(
                        request,user.getUserId()))
                .pageNum(request.getPage())
                .pageSize(request.getNum())
                .orderList(orderList)
                .build();
    }

    @Override
    public OrderDetailResponse selectMyOrderDetail(Long orderId,User user) {
        if(user == null || user.getRole() != Role.NORMAL_USER){
            throw new ApiHttpException(HttpStatus.FORBIDDEN,403,"无权访问");
        }
        if(orderId == null || orderId <= 0){
            throw myOrderBadRequest("orderId必须为正整数");
        }

        Map<String,Object> orderDetail = orderMapper.selectMyOrderDetail(
                orderId,user.getUserId()
        );
        if(orderDetail == null){
            // 不区分订单不存在与属于其他用户，避免泄露订单是否存在。
            throw new ApiHttpException(HttpStatus.NOT_FOUND,404,"订单不存在");
        }
        List<OrderDetailItem> itemList = orderMapper
                .selectOrderDetailItemList(orderId,List.of())
                .stream()
                .map(this::toOrderDetailItem)
                .toList();
        return buildOwnOrderDetail(orderDetail,itemList);
    }

    private void validateMyOrderListRequest(MyOrderListRequest request) {
        if(request == null){
            throw myOrderBadRequest("请求参数不能为空");
        }
        if(request.getPage() == null || request.getPage() < 1){
            throw myOrderBadRequest("页码必须从1开始");
        }
        if(request.getNum() == null || request.getNum() < 1
                || request.getNum() > 100){
            throw myOrderBadRequest("每页条数必须在1到100之间");
        }

        LocalDate startTime = request.getStartTime() == null
                ? BUSINESS_START_DATE : request.getStartTime();
        LocalDate endTime = request.getEndTime() == null
                ? BUSINESS_DATE : request.getEndTime();
        if(startTime.isAfter(endTime)){
            throw myOrderBadRequest("开始时间不能晚于结束时间");
        }
        if(startTime.isBefore(BUSINESS_START_DATE)
                || endTime.isAfter(BUSINESS_DATE)){
            throw myOrderBadRequest("日期超出允许范围");
        }
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        String keyWord = request.getKeyWord();
        if(keyWord != null){
            keyWord = keyWord.trim();
            if(keyWord.length() > 100){
                throw myOrderBadRequest("关键字最多100个字符");
            }
            request.setKeyWord(keyWord.isEmpty() ? null : keyWord);
        }

        List<String> requestedStatuses = request.getStatuses();
        if(requestedStatuses == null){
            request.setStatuses(ALL_ORDER_STATUSES);
        }else{
            if(requestedStatuses.isEmpty()
                    || requestedStatuses.stream().anyMatch(status ->
                    status == null || status.isBlank()
                            || !ALL_ORDER_STATUSES.contains(status.trim()))){
                throw myOrderBadRequest("存在未知订单状态");
            }
            Set<String> statusSet = requestedStatuses.stream()
                    .map(String::trim)
                    .collect(Collectors.toSet());
            request.setStatuses(ALL_ORDER_STATUSES.stream()
                    .filter(statusSet::contains)
                    .toList());
        }
        try{
            request.setOffset(Math.multiplyExact(
                    request.getPage()-1,request.getNum()
            ));
        }catch (ArithmeticException e){
            throw myOrderBadRequest("页码超出允许范围");
        }
    }

    private MyOrderListItem toMyOrderListItem(MyOrderListRow row) {
        boolean hasDetails = row.getDetailCount() != null
                && row.getDetailCount() > 0;
        BigDecimal originalAmount = toAmount(row.getOriginalAmount());
        BigDecimal amount = hasDetails
                ? toAmount(row.getItemsTotalAmount())
                : originalAmount;
        Boolean hasAmountDifference = hasDetails
                ? amount.subtract(originalAmount).abs()
                .compareTo(new BigDecimal("0.01")) > 0
                : null;
        long productCount = row.getProductCount() == null
                ? 0L : row.getProductCount();

        return MyOrderListItem.builder()
                .orderId(row.getOrderId())
                .amount(amount)
                .originalAmount(originalAmount)
                .visibleAmount(amount)
                .matchedAmount(amount)
                .contentScope("OWN")
                .status(row.getStatus())
                .createTime(row.getCreateTime())
                .userId(row.getUserId())
                .userName(row.getUserName())
                .payWay(row.getPayWay())
                .productSummary(formatProductSummary(
                        row.getProductSummary(),productCount))
                .productCount(productCount)
                .hasDetails(hasDetails)
                .hasAmountDifference(hasAmountDifference)
                .build();
    }

    private String formatProductSummary(String summary,long productCount) {
        if(summary == null || summary.isBlank()){
            return "";
        }
        String[] names = summary.split("、");
        if(names.length <= 2){
            return summary;
        }
        return names[0]+"、"+names[1]+"等 "+productCount+" 种商品";
    }

    private OrderDetailResponse buildOwnOrderDetail(
            Map<String,Object> orderDetail,
            List<OrderDetailItem> itemList) {
        BigDecimal originalAmount = toAmount(orderDetail.get("originalAmount"));
        BigDecimal itemsTotalAmount = itemList.isEmpty()
                ? null
                : itemList.stream()
                .map(OrderDetailItem::getItemTotal)
                .reduce(BigDecimal.ZERO,(a,b) -> a.add(b)
                        .setScale(2,RoundingMode.HALF_UP));
        Long quantity = itemList.isEmpty()
                ? null
                : itemList.stream().mapToLong(OrderDetailItem::getQuantity).sum();

        OrderDetailResponse.OrderDetailResponseBuilder result = OrderDetailResponse
                .builder()
                .orderId(((Number) orderDetail.get("orderId")).longValue())
                .status(orderDetail.get("status").toString())
                .createTime(toLocalDateTime(orderDetail.get("createTime")))
                .userId(((Number) orderDetail.get("userId")).longValue())
                .userName(orderDetail.get("userName") == null
                        ? null : orderDetail.get("userName").toString())
                .payWay(orderDetail.get("payWay") == null
                        ? null : orderDetail.get("payWay").toString())
                .contentScope("OWN")
                .originalAmount(originalAmount)
                .hasDetails(!itemList.isEmpty())
                .itemCount((long) itemList.size())
                .items(itemList);

        if(itemList.isEmpty()){
            return result
                    .amount(originalAmount)
                    .itemsTotalAmount(null)
                    .amountDifference(null)
                    .hasAmountDifference(null)
                    .visibleAmount(originalAmount)
                    .visibleQuantity(null)
                    .totalQuantity(null)
                    .amountSource("ORDER_TOTAL")
                    .build();
        }
        BigDecimal difference = itemsTotalAmount.subtract(originalAmount)
                .setScale(2,RoundingMode.HALF_UP);
        return result
                .amount(itemsTotalAmount)
                .itemsTotalAmount(itemsTotalAmount)
                .amountDifference(difference)
                .hasAmountDifference(difference.abs()
                        .compareTo(new BigDecimal("0.01")) > 0)
                .visibleAmount(itemsTotalAmount)
                .visibleQuantity(quantity)
                .totalQuantity(quantity)
                .amountSource("ITEM_SUM")
                .build();
    }

    private ApiHttpException myOrderBadRequest(String message) {
        return new ApiHttpException(HttpStatus.BAD_REQUEST,
                Code.ParamError.getCode(),Code.ParamError.getDesc()+":"+message);
    }

    private OrderDetailItem toOrderDetailItem(Map<String,Object> item){
        String category = item.get("category").toString();
        return OrderDetailItem.builder()
                .orderItemId(((Number) item.get("orderItemId")).longValue())
                .quantity(((Number) item.get("quantity")).longValue())
                .itemPrice(toAmount(item.get("itemPrice")))
                .itemTotal(toAmount(item.get("itemTotal")))
                .product(OrderDetailItem.ProductInfo.builder()
                        .productId(((Number) item.get("productId")).longValue())
                        .productName(item.get("productName").toString())
                        .category(category)
                        .categoryLabel(ProductCategory.fromValue(category).getLabel())
                        .brand(item.get("brand").toString())
                        .currentPrice(toAmount(item.get("currentPrice")))
                        .rating(new BigDecimal(item.get("rating").toString()))
                        .build())
                .build();
    }

    private OrderPaymentStatsList toOrderPaymentStatsList(Map<String,Object> item){
        String payWay = item.get("payWay") == null
                ? null
                : item.get("payWay").toString();
        return OrderPaymentStatsList.builder()
                .payWay(payWay)
                .payWayLabel(payWay == null ? "未知" : payWay)
                .orderCount(((Number) item.get("orderCount")).longValue())
                .build();
    }

    private int getPayWayOrder(String payWay){
        if(payWay == null){
            return 3;
        }
        return switch (payWay){
            case "银行卡支付" -> 0;
            case "微信支付" -> 1;
            case "支付宝支付" -> 2;
            default -> 3;
        };
    }

    private BigDecimal getRatio(long count,long total){
        if(total == 0){
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(count).divide(
                BigDecimal.valueOf(total),6,RoundingMode.HALF_UP
        );
    }
    // 调整为它所在日期或之前最近的一个时间范围
    private LocalDate getPeriodStart(LocalDate date,String granularity){
        return switch (granularity){
            case "week" -> date.with(TemporalAdjusters
                    .previousOrSame(DayOfWeek.MONDAY));
            case "month" -> date.withDayOfMonth(1);
            default -> date;
        };
    }
    // 调整为它所在日期一个时间范围内最后的日期
    private LocalDate getPeriodEnd(LocalDate date,String granularity){
        return switch (granularity){
            case "week" -> date.plusDays(6);
            case "month" -> date.with(TemporalAdjusters.lastDayOfMonth());
            default -> date;
        };
    }
    // 增加下一个日期
    private LocalDate getNextPeriod(LocalDate date,String granularity){
        return switch (granularity){
            case "week" -> date.plusWeeks(1);
            case "month" -> date.plusMonths(1);
            default -> date.plusDays(1);
        };
    }
    // 类型转化
    private LocalDate toLocalDate(Object date){
        if(date instanceof LocalDate localDate){
            return localDate;
        }
        if(date instanceof Date sqlDate){
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(date.toString());
    }

    private LocalDateTime toLocalDateTime(Object date){
        if(date instanceof LocalDateTime localDateTime){
            return localDateTime;
        }
        if(date instanceof java.sql.Timestamp timestamp){
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(date.toString()
                .replace(" ","T"));
    }

    private BigDecimal toAmount(Object amount){
        return new BigDecimal(amount.toString())
                .setScale(2,RoundingMode.HALF_UP);
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
