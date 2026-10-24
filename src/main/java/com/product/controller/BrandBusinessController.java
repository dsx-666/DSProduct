package com.product.controller;

import com.product.MyException.ApiHttpException;
import com.product.enums.Code;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.vo.Result;
import com.product.service.BrandBusinessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/brand")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class BrandBusinessController {
    private final BrandBusinessService brandBusinessService;

    // 返回当前账号可看的品牌历史新老客占比。
    @GetMapping("/customer-segments")
    public Result<Map<String,Object>> getCustomerSegments(
            @AuthenticationPrincipal MyUserDetails principal,
            @RequestParam(required = false) String brand,
            HttpServletRequest request) {
        validateParameters(request, Set.of("brand"));
        if(request.getParameterValues("brand") != null
                && request.getParameterValues("brand").length > 1)
            badRequest("brand只允许传一个值");
        return Result.success(brandBusinessService.getCustomerSegments(principal.getUser(), brand));
    }

    // 接收分页和排序条件，返回品牌商品经营列表。
    @GetMapping("/products")
    public Result<Map<String,Object>> getProducts(
            @AuthenticationPrincipal MyUserDetails principal,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer num,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endTime,
            @RequestParam(required = false, name = "brand") List<String> brands,
            @RequestParam(required = false) String keyWord,
            @RequestParam(defaultValue = "salesVolume") String sortBy,
            @RequestParam(defaultValue = "desc") String order,
            HttpServletRequest request) {
        validateParameters(request, Set.of("page", "num", "range", "startTime", "endTime",
                "brand", "keyWord", "sortBy", "order"));
        require(page, "page"); require(num, "num"); require(range, "range");
        return Result.success(brandBusinessService.getProducts(principal.getUser(), page, num,
                range, startTime, endTime, brands, keyWord, sortBy, order));
    }

    // 返回一个已授权商品的经营详情。
    @GetMapping("/products/{productId}")
    public Result<Map<String,Object>> getProductDetail(
            @AuthenticationPrincipal MyUserDetails principal,
            @PathVariable long productId, @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endTime,
            HttpServletRequest request) {
        validateParameters(request, Set.of("range", "startTime", "endTime"));
        require(range, "range");
        return Result.success(brandBusinessService.getProductDetail(principal.getUser(), productId,
                range, startTime, endTime));
    }

    // 分页返回商品评价，供详情页查看全部评价。
    @GetMapping("/products/{productId}/reviews")
    public Result<Map<String,Object>> getReviews(
            @AuthenticationPrincipal MyUserDetails principal,
            @PathVariable long productId, @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "20") int num,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endTime,
            @RequestParam(required = false) Integer rating,
            HttpServletRequest request) {
        validateParameters(request, Set.of("page", "num", "startTime", "endTime", "rating"));
        require(page, "page");
        return Result.success(brandBusinessService.getReviews(principal.getUser(), productId,
                page, num, startTime, endTime, rating));
    }

    // 分页返回购买过该商品的去重用户。
    @GetMapping("/products/{productId}/buyers")
    public Result<Map<String,Object>> getBuyers(
            @AuthenticationPrincipal MyUserDetails principal,
            @PathVariable long productId, @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "20") int num,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endTime,
            @RequestParam(required = false) String keyWord,
            HttpServletRequest request) {
        validateParameters(request, Set.of("page", "num", "range", "startTime", "endTime", "keyWord"));
        require(page, "page"); require(range, "range");
        return Result.success(brandBusinessService.getBuyers(principal.getUser(), productId,
                page, num, range, startTime, endTime, keyWord));
    }

    // 拒绝接口文档之外的查询参数，避免前端误传条件。
    private void validateParameters(HttpServletRequest request, Set<String> supported) {
        if(!supported.containsAll(request.getParameterMap().keySet()))
            badRequest("存在不支持的参数");
    }

    // 统一构造参数错误响应。
    private void badRequest(String message) {
        throw new ApiHttpException(HttpStatus.BAD_REQUEST, Code.ParamError.getCode(), message);
    }

    // 手动检查必须参数，让缺参也返回项目统一错误格式。
    private void require(Object value, String name) {
        if(value == null) badRequest("缺少必选参数:" + name);
    }
}
