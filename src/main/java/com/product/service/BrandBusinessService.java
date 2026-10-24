package com.product.service;

import com.product.pojo.po.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BrandBusinessService {
    // 统计品牌历史购买次数对应的新老客占比。
    Map<String,Object> getCustomerSegments(User user, String brand);
    // 分页查询当前账号有权查看的品牌商品。
    Map<String,Object> getProducts(User user, int page, int num, String range,
            LocalDate startTime, LocalDate endTime, List<String> brands,
            String keyWord, String sortBy, String order);
    // 查询单个商品的经营、人群和评价概览。
    Map<String,Object> getProductDetail(User user, long productId, String range,
            LocalDate startTime, LocalDate endTime);
    // 分页查询指定商品的评价。
    Map<String,Object> getReviews(User user, long productId, int page, int num,
            LocalDate startTime, LocalDate endTime, Integer rating);
    // 分页查询指定商品的去重购买用户。
    Map<String,Object> getBuyers(User user, long productId, int page, int num,
            String range, LocalDate startTime, LocalDate endTime, String keyWord);
}
