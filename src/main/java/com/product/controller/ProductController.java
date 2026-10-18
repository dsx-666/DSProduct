package com.product.controller;

import com.product.MyException.BusinessException;
import com.product.enums.Code;
import com.product.enums.ProductCategory;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.request.ProductRankingRequest;
import com.product.pojo.dto.response.ProductBrandResponse;
import com.product.pojo.dto.response.ProductCategoryStatsResponse;
import com.product.pojo.dto.response.ProductRankingResponse;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.vo.ProductBrandVo;
import com.product.pojo.vo.ProductCategoryStatsVo;
import com.product.pojo.vo.ProductRankingVo;
import com.product.pojo.vo.Result;
import com.product.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductService productService;
    @GetMapping("/categories")
    public Result<List<ProductCategory>> getCategories() {
        // productService.getCategories()返回的是一个List<String>
        return Result.success(productService
                .getCategories());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/product/ranking")
    public Result<ProductRankingVo> getProductRanking(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @ModelAttribute ProductRankingRequest productRankingRequest
    ) {
        ProductRankingResponse productRankingResponse =
                productService.getProductRanking(productRankingRequest,myUserDetails.getUser());

        return Result.success(
                ProductRankingVo.builder()
                        .filter(productRankingRequest.getFilter())
                        .scope(productRankingResponse.getScope())
                        .metric(productRankingRequest.getMetric())
                        .limit((long) 10)
                        .soldProductCount(productRankingResponse.getSoldProductCount())
                        .hotProductCount(productRankingResponse.getHotProductCount())
                        .rankList(productRankingResponse.getRankList())
                        .build()
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/product/category-stats")
    public Result<ProductCategoryStatsVo> getProductCategoryStats(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @ModelAttribute Filter filter) {
        ProductCategoryStatsResponse productCategoryStatsResponse = productService
                .getProductCategoryStats(filter,myUserDetails.getUser());

        return Result.success(
                ProductCategoryStatsVo.builder()
                        .filters(filter)
                        .scope(productCategoryStatsResponse.getScope())
                        .totalSalesAmount(productCategoryStatsResponse
                                .getTotalSalesAmount())
                        .categoryList(productCategoryStatsResponse.getCategoryList())
                        .build()
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/brands")
    public Result<ProductBrandVo> getBrands(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            HttpServletRequest request) {
        if(!request.getParameterMap().isEmpty()){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":存在不支持的参数");
        }

        ProductBrandResponse productBrandResponse = productService
                .getBrands(myUserDetails.getUser());

        return Result.success(
                ProductBrandVo.builder()
                        .brands(productBrandResponse.getBrands())
                        .scope(productBrandResponse.getScope())
                        .build()
        );
    }


}
