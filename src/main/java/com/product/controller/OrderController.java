package com.product.controller;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.dto.request.KpiAnalysisRequest;
import com.product.pojo.dto.response.OrderListResponse;
import com.product.pojo.vo.OrderKpiVo;
import com.product.pojo.vo.Result;
import com.product.pojo.vo.OrderListVo;
import com.product.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @GetMapping("/orders")
    /*
    @RequestParam 的作用是把 URL 查询参数（如 ?page=1&size=10）绑定到单个简单类型的参数上。
     */
    public Result<OrderListVo> selectOrdersInfo(
            OrderListRequest orderListRequest) {
        OrderListResponse orderListResponse = orderService
                .selectOrderInfo(orderListRequest);

        return Result.success(
                OrderListVo.builder()
                        .orderList(orderListResponse.getOrderList())
                        .total(orderListResponse.getTotal())
                        .pageNum(orderListRequest.getPage())
                        .pageSize(orderListRequest.getNum())
                        .build()
        );
    }
//    @GetMapping("/order/kpi")
//    public Result<OrderKpiVo> selectOrderKpiInfo(
//            KpiAnalysisRequest kpiAnalysisRequest) {
//        OrderListResponse orderListResponse = orderService.
//
//
//        return null;
//
//    }

}
