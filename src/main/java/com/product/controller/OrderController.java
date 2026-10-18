package com.product.controller;
import com.product.MyException.BusinessException;
import com.product.MyException.ApiHttpException;
import com.product.enums.Code;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.request.MyOrderListRequest;
import com.product.pojo.dto.response.OrderDetailResponse;
import com.product.pojo.dto.response.OrderKpiResponse;
import com.product.pojo.dto.response.OrderListResponse;
import com.product.pojo.dto.response.OrderPaymentStatsResponse;
import com.product.pojo.dto.response.MyOrderListResponse;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.vo.OrderDetailVo;
import com.product.pojo.vo.OrderKpiVo;
import com.product.pojo.vo.OrderPaymentStatsVo;
import com.product.pojo.vo.Result;
import com.product.pojo.vo.OrderListVo;
import com.product.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

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
            @RequestParam(required = false) String granularity) {

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

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/orders/payment-stats")
    public Result<OrderPaymentStatsVo> selectOrderPaymentStats(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @ModelAttribute Filter filter,
            HttpServletRequest request) {
        Set<String> allowedParams = Set.of(
                "startTime","endTime","category","brand"
        );
        if(!allowedParams.containsAll(request.getParameterMap().keySet())){
            throw new ApiHttpException(HttpStatus.BAD_REQUEST,
                    Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":存在不支持的参数");
        }

        OrderPaymentStatsResponse orderPaymentStatsResponse = orderService
                .selectOrderPaymentStats(filter,myUserDetails.getUser());

        return Result.success(
                OrderPaymentStatsVo.builder()
                        .filters(filter)
                        .scope(orderPaymentStatsResponse.getScope())
                        .totalOrderCount(orderPaymentStatsResponse
                                .getTotalOrderCount())
                        .payMethodList(orderPaymentStatsResponse.getPayMethodList())
                        .build()
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/orders/{orderId}")
    public Result<OrderDetailVo> selectOrderDetail(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @PathVariable Long orderId,
            HttpServletRequest request) {
        if(!request.getParameterMap().isEmpty()){
            throw new ApiHttpException(HttpStatus.BAD_REQUEST,
                    Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":存在不支持的参数");
        }

        OrderDetailResponse orderDetailResponse = orderService
                .selectOrderDetail(orderId,myUserDetails.getUser());

        return Result.success(
                OrderDetailVo.builder()
                        .orderId(orderDetailResponse.getOrderId())
                        .status(orderDetailResponse.getStatus())
                        .createTime(orderDetailResponse.getCreateTime())
                        .userId(orderDetailResponse.getUserId())
                        .userName(orderDetailResponse.getUserName())
                        .payWay(orderDetailResponse.getPayWay())
                        .contentScope(orderDetailResponse.getContentScope())
                        .amount(orderDetailResponse.getAmount())
                        .originalAmount(orderDetailResponse.getOriginalAmount())
                        .itemsTotalAmount(orderDetailResponse.getItemsTotalAmount())
                        .amountDifference(orderDetailResponse.getAmountDifference())
                        .hasAmountDifference(orderDetailResponse
                                .getHasAmountDifference())
                        .visibleAmount(orderDetailResponse.getVisibleAmount())
                        .visibleQuantity(orderDetailResponse.getVisibleQuantity())
                        .totalQuantity(orderDetailResponse.getTotalQuantity())
                        .amountSource(orderDetailResponse.getAmountSource())
                        .hasDetails(orderDetailResponse.getHasDetails())
                        .itemCount(orderDetailResponse.getItemCount())
                        .items(orderDetailResponse.getItems())
                        .build()
        );
    }

    @PreAuthorize("hasRole('NORMAL_USER')")
    @GetMapping("/me/orders")
    public Result<MyOrderListResponse> selectMyOrders(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @ModelAttribute MyOrderListRequest orderListRequest,
            HttpServletRequest request) {
        Set<String> allowedParams = Set.of(
                "page","num","startTime","endTime","keyWord","statuses"
        );
        if(!allowedParams.containsAll(request.getParameterMap().keySet())){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":存在不支持的参数");
        }

        return Result.success(orderService.selectMyOrderList(
                orderListRequest,myUserDetails.getUser()
        ));
    }

    @PreAuthorize("hasRole('NORMAL_USER')")
    @GetMapping("/me/orders/{orderId}")
    public Result<OrderDetailVo> selectMyOrderDetail(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @PathVariable Long orderId,
            HttpServletRequest request) {
        if(!request.getParameterMap().isEmpty()){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":存在不支持的参数");
        }
        OrderDetailResponse response = orderService.selectMyOrderDetail(
                orderId,myUserDetails.getUser()
        );
        return Result.success(toOrderDetailVo(response));
    }

    private OrderDetailVo toOrderDetailVo(OrderDetailResponse response) {
        return OrderDetailVo.builder()
                .orderId(response.getOrderId())
                .status(response.getStatus())
                .createTime(response.getCreateTime())
                .userId(response.getUserId())
                .userName(response.getUserName())
                .payWay(response.getPayWay())
                .contentScope(response.getContentScope())
                .amount(response.getAmount())
                .originalAmount(response.getOriginalAmount())
                .itemsTotalAmount(response.getItemsTotalAmount())
                .amountDifference(response.getAmountDifference())
                .hasAmountDifference(response.getHasAmountDifference())
                .visibleAmount(response.getVisibleAmount())
                .visibleQuantity(response.getVisibleQuantity())
                .totalQuantity(response.getTotalQuantity())
                .amountSource(response.getAmountSource())
                .hasDetails(response.getHasDetails())
                .itemCount(response.getItemCount())
                .items(response.getItems())
                .build();
    }

}
