package com.product.service;
import com.product.enums.ProductCategory;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.bo.Scope;
import com.product.pojo.dto.request.ProductRankingRequest;
import com.product.pojo.dto.response.ProductBrandResponse;
import com.product.pojo.dto.response.ProductCategoryStatsResponse;
import com.product.pojo.dto.response.ProductRankingResponse;
import com.product.pojo.dto.request.ReviewInsightsRequest;
import com.product.pojo.dto.request.CustomerSalesRankingRequest;
import com.product.pojo.dto.response.ReviewInsightsResponse;
import com.product.pojo.dto.response.CustomerSalesRankingResponse;
import com.product.pojo.po.User;

import java.util.List;
import java.util.Map;

public interface ProductService {
    List<ProductCategory> getCategories();

    List<Map<String,Long>> getProductCount(Scope scope);

    List<Object> selectProductIdByFilter(Filter filter);

    ProductRankingResponse getProductRanking(ProductRankingRequest productRankingRequest,
                                             User user);

    ProductCategoryStatsResponse getProductCategoryStats(Filter filter,User user);

    ProductBrandResponse getBrands(User user);

    ReviewInsightsResponse getReviewInsights(ReviewInsightsRequest request, User user);

    CustomerSalesRankingResponse getCustomerSalesRanking(CustomerSalesRankingRequest request, User user);
}
