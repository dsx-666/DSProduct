package com.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.product.MyException.BusinessException;
import com.product.config.BusinessProperties;
import com.product.enums.Code;
import com.product.mapper.OrderMapper;
import com.product.pojo.dto.request.KpiAnalysisRequest;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.dto.response.OrderListResponse;
import com.product.pojo.other.QueryOrderListResult;
import com.product.pojo.po.Order;
import com.product.service.OrderService;
import com.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final ProductService productService;
    private final BusinessProperties businessProperties;


    @Override
    public OrderListResponse selectOrderInfo(
            OrderListRequest orderListRequest) {
        // 定义null为业务默认
        if(orderListRequest.getStartTime()==null){
            orderListRequest.setStartTime(businessProperties.getStartTime());
        }
        if(orderListRequest.getEndTime()==null){
            orderListRequest.setEndTime(businessProperties.getTime());
        }
        // 参数业务校验(防止后续的缓存击穿)
        // 时间参数
        /*
        TODO: 以后项目大了可以尝试转成Bo进行业务逻辑校验，这个OrderBo也可以加入ProductBo
         并且可以再封装一个方法调用属性的方法省的去.属性.方法
        */

        if(orderListRequest
                .getEndTime()
                .isBefore(orderListRequest
                        .getStartTime())){

                throw new BusinessException(Code.ParamError.getCode(),
                        Code.ParamError.getDesc()+":开始时间超出结束时间");
        }

        // 状态参数
        if(orderListRequest.getStatuses()!=null){
            Set<String> status = this.selectStatus()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toCollection(HashSet::new));
            // set要快一些
            if(!status.containsAll(orderListRequest.getStatuses())){
                throw new BusinessException(Code.ParamError.getCode()
                        ,Code.ParamError.getDesc()+":存在未知状态");
            }
        }

        return OrderListResponse.builder()
                .orderList(
                        orderMapper
                        .selectOrderList(orderListRequest))
                .total(
                        orderMapper
                        .selectOrderListCount(orderListRequest))
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
