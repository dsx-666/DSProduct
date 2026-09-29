package com.product.service;

import com.product.pojo.dto.request.KpiAnalysisRequest;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.dto.response.OrderListResponse;

import java.util.List;

public interface OrderService {
    OrderListResponse selectOrderInfo(
            OrderListRequest orderListRequest);
    List<Object> selectStatus();

    OrderKpiResponse selectOrderKpi(
            KpiAnalysisRequest kpiAnalysisRequest);

}
