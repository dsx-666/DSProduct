package com.product.service.impl;

import com.product.MyException.BusinessException;
import com.product.converter.UserConverter;
import com.product.enums.Code;
import com.product.enums.ProductCategory;
import com.product.enums.Role;
import com.product.mapper.ProductMapper;
import com.product.pojo.bo.ProductCategoryStatsList;
import com.product.pojo.bo.ProductRankingList;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.bo.Scope;
import com.product.pojo.dto.request.ProductRankingRequest;
import com.product.pojo.dto.response.ProductBrandResponse;
import com.product.pojo.dto.response.ProductCategoryStatsResponse;
import com.product.pojo.dto.response.ProductRankingResponse;
import com.product.pojo.po.User;
import com.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
@Slf4j
public class ProductServiceImpl implements ProductService {
    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final FilterValidator filterValidator;
    @Override
    public List<ProductCategory> getCategories() {
        return productMapper.selectCategories()
                .stream()
                .map(Object::toString)
                .map(ProductCategory::fromValue)
                        .toList();
    }
    // 按升序进行获取productCount
    @Override
    public List<Map<String, Long>> getProductCount(Scope scope) {
        String redisKey = "product:count:ranking:" + getScopeCacheKey(scope);
        Boolean exists = stringRedisTemplate.hasKey(redisKey);
        if (!Boolean.TRUE.equals(exists)) {
            // 获取数据
            List<Map<String, Object>> productCount =
                    productMapper.selectAllProductCount(scope);

            for (Map<String, Object> item : productCount) {
                String productId = String.valueOf(item.get("productId"));
                Number count = (Number) item.get("productCount");

                stringRedisTemplate.opsForZSet().add(
                        redisKey,
                        productId,
                        count == null ? 0 : count.doubleValue()
                );
            }
            // 所有数据写入完成后，统一设置过期时间
            stringRedisTemplate.expire(
                    redisKey,
                    1,
                    TimeUnit.HOURS
            );
        }
        // 获取数据
        Set<ZSetOperations.TypedTuple<String>> result =
                stringRedisTemplate.opsForZSet()
                        .reverseRangeWithScores(redisKey, 0, -1);

        List<Map<String, Long>> list = new ArrayList<>();

        if (result != null) {
            for (ZSetOperations.TypedTuple<String> item : result) {
                String productId = item.getValue();
                Double score = item.getScore();

                if (productId == null || score == null) {
                    continue;
                }

                Map<String, Long> map = new HashMap<>();
                map.put("productId", Long.valueOf(productId));
                map.put("productCount", score.longValue());

                list.add(map);
            }
        }

        return list;
    }

    @Override
    public List<Object> selectProductIdByFilter(Filter filter){
        return productMapper.selectProductId(filter);
    }

    @Override
    public ProductRankingResponse getProductRanking(ProductRankingRequest productRankingRequest,
                                                    User user) {


        if(!List.of("amount","quantity").contains(productRankingRequest.getMetric())){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":metric只支持amount或quantity");
        }

        Filter filter = productRankingRequest.getFilter();
        filter.setStatuses(List.of("completed"));
        filterValidator.validate(filter);

        Scope scope = UserConverter.toScope(user);
        if(!scope.validateFilter(filter)){
            throw new BusinessException(Code.AccessDeniedError.getCode(),
                    Code.AccessDeniedError.getDesc());
        }



        List<ProductRankingList> productRankingList =
                getProductRankingList(filter,scope);
        /*
        * 创建一个商品排行榜的比较器，按照多个条件依次排序
        *
        * */
        Comparator<ProductRankingList> hotComparator =
                Comparator.comparing(ProductRankingList::getSalesVolume,
                                Comparator.reverseOrder())
                        .thenComparing(ProductRankingList::getSalesAmount,
                                Comparator.reverseOrder())
                        .thenComparing(item -> item.getProduct().getProductId());
        // 调整回来了
        List<Map<String,Long>> productCount =  this.getProductCount(scope);
        long hotProductCount = (long) Math.ceil(productCount.size() * 0.3);
        productCount = productCount.subList(0,(int) hotProductCount);

        Set<Long> hotProductIds = productCount.stream()
                .map(item -> item.get("productId"))
                .collect(Collectors.toSet());
        log.info("hotProductIds:{}", hotProductIds);
        productRankingList.forEach(item -> item.setIsHot(
                hotProductIds.contains(item.getProduct().getProductId())
        ));

        Comparator<ProductRankingList> amountComparator =
                Comparator.comparing(ProductRankingList::getSalesAmount,
                                Comparator.reverseOrder())
                        .thenComparing(ProductRankingList::getSalesVolume,
                                Comparator.reverseOrder())
                        .thenComparing(item -> item.getProduct().getProductId());

        Comparator<ProductRankingList> quantityComparator =
                Comparator.comparing(ProductRankingList::getSalesVolume,
                                Comparator.reverseOrder())
                        .thenComparing(ProductRankingList::getSalesAmount,
                                Comparator.reverseOrder())
                        .thenComparing(item -> item.getProduct().getProductId());

        List<ProductRankingList> rankList = productRankingList.stream()
                .sorted("amount".equals(productRankingRequest.getMetric())
                        ? amountComparator
                        : quantityComparator)
                .limit(10)
                .toList();

        IntStream.range(0,rankList.size())
                .forEach(i -> rankList.get(i).setRank((long) i + 1));

        return ProductRankingResponse.builder()
                .soldProductCount((long) productRankingList.size())
                .hotProductCount(hotProductCount)
                .rankList(rankList)
                .scope(scope)
                .build();
    }

    @Override
    public ProductCategoryStatsResponse getProductCategoryStats(Filter filter,User user) {
        filter.setStatuses(List.of("completed"));
        filterValidator.validate(filter);

        Scope scope = UserConverter.toScope(user);
        if(!scope.validateFilter(filter)){
            throw new BusinessException(Code.AccessDeniedError.getCode(),
                    Code.AccessDeniedError.getDesc());
        }

        List<ProductCategoryStatsList> categoryList = productMapper
                .selectProductCategoryStats(filter,scope)
                .stream()
                .map(this::toProductCategoryStatsList)
                .collect(Collectors.toCollection(ArrayList::new));

        if("ALL".equals(scope.getMode())
            && filter.getBrands().isEmpty()
            && filter.getCategories().isEmpty()){
            Map<String,Object> uncategorizedStats =
                    productMapper.selectUncategorizedStats(filter);
            if(uncategorizedStats != null
                    && uncategorizedStats.get("uniqueOrderCount") != null
                    && ((Number) uncategorizedStats.get("uniqueOrderCount")).longValue() > 0){
                categoryList.add(ProductCategoryStatsList.builder()
                        .category(null)
                        .categoryName("未归类")
                        .isUncategorized(true)
                        .salesAmount(toAmount(uncategorizedStats.get("salesAmount")))
                        .salesVolume(null)
                        .uniqueOrderCount(((Number) uncategorizedStats
                                .get("uniqueOrderCount")).longValue())
                        .build());
            }
        }

        categoryList.sort(Comparator
                .comparing(ProductCategoryStatsList::getSalesAmount,
                        Comparator.reverseOrder())
                .thenComparing(ProductCategoryStatsList::getCategory,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        BigDecimal totalSalesAmount = categoryList.stream()
                .map(ProductCategoryStatsList::getSalesAmount)
                .reduce(BigDecimal.ZERO,(a,b) -> a.add(b)
                        .setScale(2,RoundingMode.HALF_UP));

        for(ProductCategoryStatsList item : categoryList){
            item.setSalesRatio(totalSalesAmount.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : item.getSalesAmount().divide(
                            totalSalesAmount,6,RoundingMode.HALF_UP));
        }

        return ProductCategoryStatsResponse.builder()
                .totalSalesAmount(totalSalesAmount.setScale(2,RoundingMode.HALF_UP))
                .categoryList(categoryList)
                .scope(scope)
                .build();
    }

    @Override
    public ProductBrandResponse getBrands(User user) {
        Scope scope = UserConverter.toScope(user);
        List<String> brandList = productMapper.selectBrands()
                .stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .sorted()
                .toList();

        if("BRAND".equals(scope.getMode())){
            brandList = brandList.stream()
                    .filter(scope.getAllowedBrands()::contains)
                    .toList();
            if(brandList.isEmpty()){
                scope.setAllowedBrands(List.of());
            }
        }

        List<ProductBrandResponse.BrandOption> brands = brandList.stream()
                .map(brand -> ProductBrandResponse.BrandOption.builder()
                        .value(brand)
                        .label(brand)
                        .build())
                .toList();

        return ProductBrandResponse.builder()
                .brands(brands)
                .scope(scope)
                .build();
    }

    private ProductCategoryStatsList toProductCategoryStatsList(
            Map<String,Object> item){
        String category = item.get("category").toString();
        return ProductCategoryStatsList.builder()
                .category(category)
                .categoryName(ProductCategory.fromValue(category).getLabel())
                .isUncategorized(false)
                .salesAmount(toAmount(item.get("salesAmount")))
                .salesVolume(((Number) item.get("salesVolume")).longValue())
                .uniqueOrderCount(((Number) item.get("uniqueOrderCount")).longValue())
                .build();
    }

    private BigDecimal toAmount(Object amount){
        return amount == null
                ? BigDecimal.ZERO.setScale(2,RoundingMode.HALF_UP)
                : new BigDecimal(amount.toString())
                .setScale(2,RoundingMode.HALF_UP);
    }

    private List<ProductRankingList> getProductRankingList(Filter filter,
                                                           Scope scope){
        try{
            return getProductRankingListByRedis(filter,scope);
        }catch (RedisConnectionFailureException e){
            log.warn("Redis连接失败，商品排行使用数据库查询");
            return selectProductRanking(filter,scope);
        }
    }

    private List<ProductRankingList> getProductRankingListByRedis(Filter filter,
                                                                  Scope scope){
        String cacheKey = getProductRankingCacheKey(filter,scope);
        String amountRedisKey = "product:amount:ranking:" + cacheKey;
        String quantityRedisKey = "product:quantity:ranking:" + cacheKey;

        Boolean amountExists = stringRedisTemplate.hasKey(amountRedisKey);
        Boolean quantityExists = stringRedisTemplate.hasKey(quantityRedisKey);

        if(Boolean.TRUE.equals(amountExists) && Boolean.TRUE.equals(quantityExists)){
            List<ProductRankingList> cacheResult = getProductRankingFromRedis(
                    amountRedisKey,quantityRedisKey
            );
            if(cacheResult != null){
                return cacheResult;
            }
        }

        List<ProductRankingList> productRankingList =
                selectProductRanking(filter,scope);

        stringRedisTemplate.delete(List.of(amountRedisKey,quantityRedisKey));
        for(ProductRankingList item : productRankingList){
            String productId = item.getProduct().getProductId().toString();
            // Redis的score使用分，避免销售额转double时丢失小数精度
            stringRedisTemplate.opsForZSet().add(
                    amountRedisKey,
                    productId,
                    item.getSalesAmount().movePointRight(2).doubleValue()
            );
            stringRedisTemplate.opsForZSet().add(
                    quantityRedisKey,
                    productId,
                    item.getSalesVolume().doubleValue()
            );
        }

        if(!productRankingList.isEmpty()){
            stringRedisTemplate.expire(amountRedisKey,1,TimeUnit.HOURS);
            stringRedisTemplate.expire(quantityRedisKey,1,TimeUnit.HOURS);
        }

        return productRankingList;
    }

    private List<ProductRankingList> selectProductRanking(Filter filter,
                                                           Scope scope){
        return productMapper.selectProductRanking(filter,scope)
                .stream()
                .map(this::toProductRankingList)
                .toList();
    }

    private List<ProductRankingList> getProductRankingFromRedis(
            String amountRedisKey,
            String quantityRedisKey){
        Set<ZSetOperations.TypedTuple<String>> amountResult =
                stringRedisTemplate.opsForZSet()
                        .reverseRangeWithScores(amountRedisKey,0,-1);
        Set<ZSetOperations.TypedTuple<String>> quantityResult =
                stringRedisTemplate.opsForZSet()
                        .reverseRangeWithScores(quantityRedisKey,0,-1);

        if(amountResult == null || quantityResult == null
                || amountResult.size() != quantityResult.size()){
            return null;
        }

        Map<Long,BigDecimal> productAmount = new HashMap<>();
        for(ZSetOperations.TypedTuple<String> item : amountResult){
            if(item.getValue() != null && item.getScore() != null){
                productAmount.put(
                        Long.valueOf(item.getValue()),
                        BigDecimal.valueOf(item.getScore().longValue(),2)
                );
            }
        }

        Map<Long,Long> productQuantity = new HashMap<>();
        for(ZSetOperations.TypedTuple<String> item : quantityResult){
            if(item.getValue() != null && item.getScore() != null){
                productQuantity.put(
                        Long.valueOf(item.getValue()),
                        item.getScore().longValue()
                );
            }
        }

        if(!productAmount.keySet().equals(productQuantity.keySet())){
            return null;
        }

        List<Long> productIds = new ArrayList<>(productAmount.keySet());
        if(productIds.isEmpty()){
            return List.of();
        }

        Map<Long,ProductRankingList.ProductInfo> productInfoMap = productMapper
                .selectProductInfo(productIds)
                .stream()
                .map(this::toProductInfo)
                .collect(Collectors.toMap(
                        ProductRankingList.ProductInfo::getProductId,
                        Function.identity()
                ));

        if(productInfoMap.size() != productIds.size()){
            return null;
        }

        return productIds.stream()
                .map(productId -> ProductRankingList.builder()
                        .product(productInfoMap.get(productId))
                        .salesAmount(productAmount.get(productId))
                        .salesVolume(productQuantity.get(productId))
                        .build())
                .toList();
    }
    // 将product的ranking进行转化
    private ProductRankingList toProductRankingList(Map<String,Object> item){
        return ProductRankingList.builder()
                .product(toProductInfo(item))
                .salesAmount(new BigDecimal(item.get("salesAmount").toString())
                        .setScale(2, RoundingMode.HALF_UP))
                .salesVolume(((Number) item.get("salesVolume")).longValue())
                .build();
    }
    // 将product的ranking进行转化
    private ProductRankingList.ProductInfo toProductInfo(Map<String,Object> item){
        ProductRankingList.ProductInfo productInfo =
                new ProductRankingList.ProductInfo();
        productInfo.setProductId(((Number) item.get("productId")).longValue());
        productInfo.setProductName(item.get("productName").toString());
        productInfo.setCategory(item.get("category").toString());
        productInfo.setCategoryLabel(ProductCategory
                .fromValue(item.get("category").toString())
                .getLabel());
        productInfo.setBrand(item.get("brand").toString());
        return productInfo;
    }
    // 生成CacheKey
    private String getProductRankingCacheKey(Filter filter,Scope scope){
        return String.join(":",
                getScopeCacheKey(scope),
                filter.getStartTime().toString(),
                filter.getEndTime().toString(),
                String.join("|",filter.getCategories()),
                String.join("|",filter.getBrands())
        );
    }
    // 生成CacheKey
    private String getScopeCacheKey(Scope scope){
        return scope.getMode() + ":" + String.join("|",scope.getAllowedBrands());
    }


}
