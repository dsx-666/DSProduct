package com.product.service;

import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.dto.response.OrderListResponse;
import com.product.pojo.po.User;

import java.util.concurrent.ExecutionException;

public interface OrderService {
    OrderListResponse selectOrderInfo(
            OrderListRequest orderListRequest,
            User user);

    OrderKpiResponse selectOrderKpi(
            Filter filter,
            User user) throws ExecutionException, InterruptedException;

}
