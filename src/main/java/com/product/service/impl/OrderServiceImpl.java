package com.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.product.MyException.BusinessException;
import com.product.config.BusinessProperties;
import com.product.converter.UserConverter;
import com.product.enums.Code;
import com.product.mapper.OrderMapper;
import com.product.pojo.dto.request.KpiAnalysisRequest;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.dto.response.OrderListResponse;
import com.product.pojo.bo.QueryOrderListResult;
import com.product.pojo.po.Order;
import com.product.pojo.po.User;
import com.product.pojo.vo.Scope;
import com.product.service.OrderService;
import com.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final BusinessProperties businessProperties;
    private final ProductService productService;

    @Override
    public OrderListResponse selectOrderInfo(
            OrderListRequest orderListRequest,
            User user) {
        // 定义null为业务默认
        if(orderListRequest.getFilters()
                .getStartTime()==null){
            orderListRequest
                    .getFilters()
                    .setStartTime(businessProperties.getStartTime());
        }
        if(orderListRequest.getFilters()
                .getEndTime()==null){
            orderListRequest
                    .getFilters()
                    .setEndTime(businessProperties.getTime());
        }
        // 权限业务校验
        Scope scope = UserConverter.toScope(user);
        if(!scope.getAllowedBrands().isEmpty()){
            Set<String> brand = new HashSet<>(scope.getAllowedBrands());
            if(!brand.containsAll(orderListRequest.getFilters().getBrands())){
                throw new BusinessException(Code.AccessDeniedError.getCode(),
                        Code.AccessDeniedError.getDesc());
            }
        }

        // 参数业务校验(防止后续的缓存击穿)
        // 时间参数
        /*
        TODO: 以后项目大了可以尝试转成Bo进行业务逻辑校验，这个OrderBo也可以加入ProductBo
         并且可以再封装一个方法调用属性的方法省的去.属性.方法
        */

        if(orderListRequest
                .getFilters()
                .getEndTime()
                .isBefore(orderListRequest
                        .getFilters()
                        .getStartTime()
                )
        ){

                throw new BusinessException(Code.ParamError.getCode(),
                        Code.ParamError.getDesc()+":开始时间超出结束时间");
        }
        // 种类参数
        if(orderListRequest.getFilters().getCategories()!=null){
            Set<String> categories = productService.selectCategories()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toCollection(HashSet::new));
            // set要快一些
            if(!categories.containsAll(orderListRequest.getFilters().getCategories())){
                throw new BusinessException(Code.ParamError.getCode()
                        ,Code.ParamError.getDesc()+":存在未知种类");
            }
            orderListRequest.getFilters().setCategories(orderListRequest
                    .getFilters()
                    .getCategories()
                    .stream()
                    .distinct()
                    .toList());
        }


        // 状态参数
        if(orderListRequest.getFilters().getStatuses()!=null){
            Set<String> status = this.selectStatus()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toCollection(HashSet::new));
            // set要快一些
            if(!status.containsAll(orderListRequest.getFilters().getStatuses())){
                throw new BusinessException(Code.ParamError.getCode()
                        ,Code.ParamError.getDesc()+":存在未知状态");
            }
            orderListRequest.getFilters().setStatuses(orderListRequest
                    .getFilters()
                    .getStatuses()
                    .stream()
                    .distinct()
                    .toList());
        }

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
    public OrderKpiResponse selectOrderKpi(KpiAnalysisRequest kpiAnalysisRequest){



        return null;
    }
    @Override
    public List<Object> selectStatus() {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Order::getOrderStatus)
                .groupBy(Order::getOrderStatus);
        return orderMapper.selectObjs(wrapper);
    }
}
