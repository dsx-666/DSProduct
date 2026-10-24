package com.product.service;

import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.request.MyOrderListRequest;
import com.product.pojo.dto.response.OrderDetailResponse;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.dto.response.OrderListResponse;
import com.product.pojo.dto.response.OrderPaymentStatsResponse;
import com.product.pojo.dto.response.MyOrderListResponse;
import com.product.pojo.dto.request.RepurchaseStatsRequest;
import com.product.pojo.dto.response.RepurchaseStatsResponse;
import com.product.pojo.dto.response.AfterSaleStatsResponse;
import com.product.pojo.po.User;

import java.util.concurrent.ExecutionException;

public interface OrderService {
    OrderListResponse selectOrderInfo(
            OrderListRequest orderListRequest,
            User user);

    OrderKpiResponse selectOrderKpi(
            Filter filter,
            User user) throws ExecutionException, InterruptedException;

    OrderKpiResponse.OrderStatsResponse selectOrderStats(
            Filter filter,
            String granularity,
            User user);

    OrderPaymentStatsResponse selectOrderPaymentStats(Filter filter,User user);

    OrderDetailResponse selectOrderDetail(Long orderId,User user);

    MyOrderListResponse selectMyOrderList(
            MyOrderListRequest request,
            User user);

    OrderDetailResponse selectMyOrderDetail(Long orderId,User user);

    RepurchaseStatsResponse getRepurchaseStats(RepurchaseStatsRequest request,User user);

    AfterSaleStatsResponse getAfterSaleStats(Filter filter,User user);

}
