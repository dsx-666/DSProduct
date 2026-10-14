package com.product.controller;
import com.product.MyException.BusinessException;
import com.product.enums.Code;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.dto.response.OrderListResponse;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.vo.OrderKpiVo;
import com.product.pojo.vo.Result;
import com.product.pojo.vo.OrderListVo;
import com.product.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@RequiredArgsConstructor
/*
    相当于这两个
    @Controller
    @ResponseBody
    这个类是一个 Spring Web Controller，而且方法返回值默认直接作为 HTTP 响应数据。会序列化成json格式
*/
@RestController
/*
    这个是给当前 Controller 的所有接口统一加一个路径前缀。
*/
@RequestMapping("/api")
public class OrderController {
    private final OrderService orderService;
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/orders")
    /*
    @RequestParam 的作用是把 URL 查询参数（如 ?page=1&size=10）绑定到单个简单类型的参数上。
     */
    public Result<OrderListVo> selectOrdersInfo(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @ModelAttribute OrderListRequest orderListRequest) {
        OrderListResponse orderListResponse = orderService
                .selectOrderInfo(orderListRequest,myUserDetails.getUser());

        return Result.success(
                OrderListVo.builder()
                        .orderList(orderListResponse.getOrderList())
                        .total(orderListResponse.getTotal())
                        .pageNum(orderListRequest.getPage())
                        .pageSize(orderListRequest.getNum())
                        .keyWord(orderListRequest.getKeyWord())
                        .scope(orderListResponse.getScope())
                        .filters(orderListRequest.getFilters())
                        .build()
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/orders/kpi")
    public Result<OrderKpiVo> selectOrderKpiInfo(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @ModelAttribute Filter filter) throws ExecutionException, InterruptedException {
        filter.setStatuses(List.of("completed"));
        OrderKpiResponse orderKpiResponse = orderService.selectOrderKpi(filter,myUserDetails.getUser());
        return Result.success(
                OrderKpiVo.builder()
                        .amountMismatchOrderCount(orderKpiResponse.getAmountMismatchOrderCount())
                        .totalSalesAmount(orderKpiResponse.getTotalSalesAmount())
                        .averageOrderValue(orderKpiResponse.getAverageOrderValue())
                        .missingDetailOrderCount(orderKpiResponse.getMissingDetailOrderCount())
                        .uniqueOrderCount(orderKpiResponse.getUniqueOrderCount())
                        .hotProductCount(orderKpiResponse.getHotProductCount())
                        .filters(filter)
                        .scope(orderKpiResponse.getScope())
                        .soldProductCount(orderKpiResponse.getSoldProductCount())
                        .build()
        );

    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/orders/stats")
    public Result<OrderKpiVo.OrderStatsVo> selectOrderStats(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @ModelAttribute Filter filter,
            @RequestParam(required = false) String granularity,
            HttpServletRequest request) {
        validateOrderStatsParams(request);
        filter.setStatuses(List.of("completed"));
        OrderKpiResponse.OrderStatsResponse orderStatsResponse = orderService
                .selectOrderStats(filter,granularity,myUserDetails.getUser());

        return Result.success(
                OrderKpiVo.OrderStatsVo.builder()
                        .filters(filter)
                        .scope(orderStatsResponse.getScope())
                        .granularity(granularity)
                        .dateList(orderStatsResponse.getDateList())
                        .periodStartList(orderStatsResponse.getPeriodStartList())
                        .periodEndList(orderStatsResponse.getPeriodEndList())
                        .salesAmountList(orderStatsResponse.getSalesAmountList())
                        .orderCountList(orderStatsResponse.getOrderCountList())
                        .build()
        );
    }

    private void validateOrderStatsParams(HttpServletRequest request){
        Set<String> allowedParams = Set.of(
                "startTime","endTime","category","brand","granularity"
        );
        if(!allowedParams.containsAll(request.getParameterMap().keySet())){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":存在不支持的参数");
        }
    }

}
