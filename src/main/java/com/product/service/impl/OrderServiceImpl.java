package com.product.service.impl;
import com.product.MyException.BusinessException;
import com.product.converter.UserConverter;
import com.product.enums.Code;
import com.product.mapper.OrderMapper;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.dto.response.OrderListResponse;
import com.product.pojo.bo.QueryOrderListResult;
import com.product.pojo.po.User;
import com.product.pojo.vo.Scope;
import com.product.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final FilterValidator filterValidator;
    private final com.product.thread.MyParallelProcessor parallelProcessor;

    // 接口A2获取order数据
    @Override
    public OrderListResponse selectOrderInfo(
            OrderListRequest orderListRequest,
            User user) {
        // 参数业务校验(防止后续的缓存击穿)
        filterValidator.validate(orderListRequest.getFilters());
        // 权限业务校验
        Scope scope = UserConverter.toScope(user);
        if(!scope.getAllowedBrands().isEmpty()){
            Set<String> brand = new HashSet<>(scope.getAllowedBrands());
            if(!brand.containsAll(orderListRequest.getFilters().getBrands())){
                throw new BusinessException(Code.AccessDeniedError.getCode(),
                        Code.AccessDeniedError.getDesc());
            }
        }
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
            BigDecimal amount = new BigDecimal(map.get("amount").toString())
                    .setScale(2, RoundingMode.HALF_UP);
            // 总金额
            temp.setTotalSalesAmount(temp.getTotalSalesAmount()
                    .add(amount)
                    .setScale(2, RoundingMode.HALF_UP));
            if(map.get("matchedAmount")==null){
                // 缺明细订单
                temp.setMissingDetailOrderCount(temp.getMissingDetailOrderCount() + 1);
            }else{
                BigDecimal matchedAmount = new BigDecimal(map.get("matchedAmount").toString())
                        .setScale(2, RoundingMode.HALF_UP);
                // 差额订单
                BigDecimal difference = amount.subtract(matchedAmount).abs();
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
    // TODO:采用多线程
    @Override
    public OrderKpiResponse selectOrderKpi(Filter filter,User user) throws
            ExecutionException,
            InterruptedException {
        Scope scope = UserConverter.toScope(user);
        List<Map<String,Object>> list = orderMapper.selectOrderKpi(filter);
        OrderKpiResponse result = OrderKpiResponse.builder()
                .amountMismatchOrderCount((long) 0)
                .totalSalesAmount(BigDecimal.ZERO)
                .uniqueOrderCount((long) 0)
                .missingDetailOrderCount((long) 0)
                .build();
        result =  parallelProcessor.processInParallel(list,4,this::processKpi,result,this::mergerKpi);
        if(scope.getMode().equals("BRAND")){
            result.setMissingDetailOrderCount((long) 0);
            result.setAmountMismatchOrderCount(null);
        }

        return result;
    }

}
