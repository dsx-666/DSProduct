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
import com.product.pojo.dto.request.ReviewInsightsRequest;
import com.product.pojo.dto.request.CustomerSalesRankingRequest;
import com.product.pojo.dto.response.ReviewInsightsResponse;
import com.product.pojo.dto.response.CustomerSalesRankingResponse;
import com.product.pojo.bo.ProductReviewInsight;
import com.product.pojo.bo.CustomerSalesSegment;
import com.product.pojo.po.User;
import com.product.service.ProductService;
import com.product.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.time.Duration;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
@Slf4j
public class ProductServiceImpl implements ProductService {
    private final ProductMapper productMapper;
    private final RedisService redisService;
    private final FilterValidator filterValidator;
    // 从数据库取出去重品类，并转换成前端需要的品类枚举。
    @Override
    public List<ProductCategory> getCategories() {
        return productMapper.selectCategories()
                .stream()
                .map(Object::toString)
                .map(ProductCategory::fromValue)
                        .toList();
    }
    // 读取商品累计销量排行；缓存不可用时直接返回数据库结果。
    @Override
    public List<Map<String, Long>> getProductCount(Scope scope) {
        String redisKey = "product:count:ranking:" + getScopeCacheKey(scope);
        if (!redisService.hasKey(redisKey)) {
            // 获取数据
            List<Map<String, Object>> productCount =
                    productMapper.selectAllProductCount(scope);

            boolean cached = redisService.replaceZSet(redisKey, productCount.stream()
                    .map(item -> new RedisService.ScoredValue(
                            String.valueOf(item.get("productId")),
                            item.get("productCount") == null ? 0
                                    : ((Number)item.get("productCount")).doubleValue()))
                    .toList(), Duration.ofMinutes(50), Duration.ofMinutes(70));
            if(!cached) return productCount.stream().map(item -> {
                Map<String,Long> value = new HashMap<>();
                value.put("productId", ((Number)item.get("productId")).longValue());
                Number count = (Number)item.get("productCount");
                value.put("productCount", count == null ? 0L : count.longValue());
                return value;
            }).toList();
        }
        // 获取数据
        List<RedisService.ScoredValue> result =
                redisService.reverseRangeWithScores(redisKey, 0, -1);
        if(result == null) {
            return productMapper.selectAllProductCount(scope).stream().map(item -> {
                Map<String,Long> value = new HashMap<>();
                value.put("productId", ((Number)item.get("productId")).longValue());
                Number count = (Number)item.get("productCount");
                value.put("productCount", count == null ? 0L : count.longValue());
                return value;
            }).toList();
        }

        List<Map<String, Long>> list = new ArrayList<>();

        if (result != null) {
            for (RedisService.ScoredValue item : result) {
                Map<String, Long> map = new HashMap<>();
                map.put("productId", Long.valueOf(item.value()));
                map.put("productCount", (long)item.score());

                list.add(map);
            }
        }

        return list;
    }

    // 按页面筛选条件查出涉及的商品 ID。
    @Override
    public List<Object> selectProductIdByFilter(Filter filter){
        return productMapper.selectProductId(filter);
    }

    // 计算商品 TOP10 和热销商品数量，并确保品牌权限先于统计生效。
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

    // 按品类汇总销售额、销量和订单数，同时保留无明细订单的特殊口径。
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

    // 返回当前账号实际有权选择的品牌列表。
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

    // 分页汇总商品评价数量、平均分、星级分布和最近评价。
    @Override
    public ReviewInsightsResponse getReviewInsights(
            ReviewInsightsRequest request, User user) {
        if(request.getPage() == null || request.getPage() < 1
                || request.getNum() == null || request.getNum() < 1 || request.getNum() > 100
                || (request.getProductId() != null && request.getProductId() <= 0)) {
            throw new BusinessException(Code.ParamError.getCode(), "参数错误");
        }
        String sort = request.getSort() == null ? "reviewCount" : request.getSort();
        if(!List.of("reviewCount","averageRating","latestReview").contains(sort)) {
            throw new BusinessException(Code.ParamError.getCode(), "sort参数错误");
        }
        Filter filter = Filter.builder().startTime(request.getStartTime())
                .endTime(request.getEndTime()).categories(request.getCategories())
                .brands(request.getBrands()).build();
        filterValidator.validate(filter);
        Scope scope = UserConverter.toScope(user);
        if(!scope.validateFilter(filter)) {
            throw new BusinessException(Code.AccessDeniedError.getCode(), Code.AccessDeniedError.getDesc());
        }
        if("BRAND".equals(scope.getMode()) && scope.getAllowedBrands().isEmpty()) {
            return ReviewInsightsResponse.builder().filters(filter).scope(scope).total(0L)
                    .pageNum(request.getPage()).pageSize(request.getNum()).productList(List.of()).build();
        }
        long offset;
        try { offset = Math.multiplyExact(request.getPage()-1, request.getNum()); }
        catch (ArithmeticException e) { throw new BusinessException(Code.ParamError.getCode(), "页码超出范围"); }
        request.setOffset(offset);
        List<Map<String,Object>> rows = productMapper.selectReviewInsightProducts(
                request.getStartTime(), request.getEndTime(), filter.getCategories(),
                filter.getBrands(), request.getProductId(), sort, offset, request.getNum());
        List<Long> ids = rows.stream().map(r -> ((Number)r.get("productId")).longValue()).toList();
        Map<Long,List<ProductReviewInsight.ReviewItem>> reviewsByProduct = new HashMap<>();
        if(!ids.isEmpty()) {
            for(Map<String,Object> review : productMapper.selectInsightReviewRows(
                    ids,request.getStartTime(),request.getEndTime())) {
                long id = ((Number)review.get("productId")).longValue();
                reviewsByProduct.computeIfAbsent(id,k -> new ArrayList<>()).add(
                        ProductReviewInsight.ReviewItem.builder()
                                .rating(((Number)review.get("rating")).intValue())
                                .reviewText((String)review.get("reviewText"))
                                .reviewDate(toLocalDateTime(review.get("reviewDate"))).build());
            }
        }
        List<ProductReviewInsight> products = rows.stream().map(row -> {
            long id = ((Number)row.get("productId")).longValue();
            Map<String,Long> distribution = new LinkedHashMap<>();
            for(int rating=1;rating<=5;rating++) distribution.put(String.valueOf(rating),
                    row.get("rating"+rating)==null?0L:((Number)row.get("rating"+rating)).longValue());
            return ProductReviewInsight.builder().productId(id)
                    .productName(row.get("productName").toString())
                    .brand(row.get("brand").toString()).category(row.get("category").toString())
                    .reviewCount(((Number)row.get("reviewCount")).longValue())
                    .averageRating(row.get("averageRating")==null?null:
                            new BigDecimal(row.get("averageRating").toString()).setScale(2,RoundingMode.HALF_UP))
                    .ratingDistribution(distribution)
                    .latestReviewDate(row.get("latestReviewDate")==null?null:
                            toLocalDateTime(row.get("latestReviewDate")).toLocalDate())
                    .reviews(reviewsByProduct.getOrDefault(id,List.of())).build();
        }).toList();
        return ReviewInsightsResponse.builder().filters(filter).scope(scope)
                .total(productMapper.countReviewInsightProducts(filter.getCategories(),filter.getBrands(),request.getProductId()))
                .pageNum(request.getPage()).pageSize(request.getNum()).productList(products).build();
    }

    // 按新客、老客和异常用户分组生成商品销售排行。
    @Override
    public CustomerSalesRankingResponse getCustomerSalesRanking(
            CustomerSalesRankingRequest request, User user) {
        if(request.getLimit()==null || request.getLimit()<1 || request.getLimit()>100
                || !List.of("amount","quantity").contains(request.getMetric())) {
            throw new BusinessException(Code.ParamError.getCode(), "参数错误");
        }
        List<String> segmentOptions = List.of("NEW","OLD","UNCLASSIFIED");
        List<String> selected = request.getSegments()==null?segmentOptions:
                request.getSegments().stream().distinct().toList();
        if(selected.isEmpty() || !segmentOptions.containsAll(selected))
            throw new BusinessException(Code.ParamError.getCode(), "segment参数错误");
        Filter filter = Filter.builder().startTime(request.getStartTime()).endTime(request.getEndTime())
                .categories(request.getCategories()).brands(request.getBrands()).build();
        filter.setStatuses(List.of("completed"));
        filterValidator.validate(filter);
        Scope scope = UserConverter.toScope(user);
        if(!scope.validateFilter(filter)) throw new BusinessException(Code.AccessDeniedError.getCode(),Code.AccessDeniedError.getDesc());
        List<Map<String,Object>> rows = ("BRAND".equals(scope.getMode()) && scope.getAllowedBrands().isEmpty())
                ? List.of() : productMapper.selectCustomerSalesRankingRows(filter.getStartTime(),filter.getEndTime(),
                java.time.LocalDate.of(2025,11,14),filter.getCategories(),filter.getBrands(),
                "BRAND".equals(scope.getMode())?scope.getAllowedBrands():null);
        List<Map<String,Object>> totals = rows.isEmpty()?List.of():productMapper.selectCustomerSalesSegmentTotals(
                filter.getStartTime(),filter.getEndTime(),java.time.LocalDate.of(2025,11,14),
                filter.getCategories(),filter.getBrands(),"BRAND".equals(scope.getMode())?scope.getAllowedBrands():null);
        List<CustomerSalesSegment> data = new ArrayList<>();
        for(String segment:segmentOptions) if(selected.contains(segment)) {
            List<Map<String,Object>> segmentRows=rows.stream().filter(r->segment.equals(r.get("segment"))).toList();
            List<CustomerSalesSegment.ProductSalesRank> products=new ArrayList<>();
            Comparator<CustomerSalesSegment.ProductSalesRank> comparator="amount".equals(request.getMetric())
                    ? Comparator.comparing(CustomerSalesSegment.ProductSalesRank::getSalesAmount).reversed()
                    : Comparator.comparing(CustomerSalesSegment.ProductSalesRank::getQuantity).reversed();
            List<CustomerSalesSegment.ProductSalesRank> sorted=segmentRows.stream().map(r ->
                    CustomerSalesSegment.ProductSalesRank.builder().productId(((Number)r.get("productId")).longValue())
                            .productName(r.get("productName").toString()).brand(r.get("brand").toString())
                            .category(r.get("category").toString()).salesAmount(toAmount(r.get("salesAmount")))
                            .quantity(((Number)r.get("quantity")).longValue()).buyerCount(((Number)r.get("buyerCount")).longValue()).build())
                    .sorted(comparator.thenComparing(CustomerSalesSegment.ProductSalesRank::getProductId)).toList();
            for(int i=0;i<Math.min(request.getLimit(),sorted.size());i++) products.add(
                    CustomerSalesSegment.ProductSalesRank.builder().rank((long)i+1)
                            .productId(sorted.get(i).getProductId()).productName(sorted.get(i).getProductName())
                            .brand(sorted.get(i).getBrand()).category(sorted.get(i).getCategory())
                            .salesAmount(sorted.get(i).getSalesAmount()).quantity(sorted.get(i).getQuantity())
                            .buyerCount(sorted.get(i).getBuyerCount()).build());
            Map<String,Object> total=totals.stream().filter(r->segment.equals(r.get("segment"))).findFirst().orElse(null);
            long buyers=total==null?0L:((Number)total.get("buyerCount")).longValue();
            long orders=total==null?0L:((Number)total.get("orderCount")).longValue();
            BigDecimal amount=total==null?BigDecimal.ZERO.setScale(2):toAmount(total.get("salesAmount"));
            long quantity=total==null?0L:((Number)total.get("quantity")).longValue();
            data.add(CustomerSalesSegment.builder().segment(segment).buyerCount(buyers).orderCount(orders)
                    .salesAmount(amount).quantity(quantity).products(products).build());
        }
        return CustomerSalesRankingResponse.builder().filters(filter).scope(scope)
                .metric(request.getMetric()).segments(selected).data(data).build();
    }

    // 兼容 JDBC 时间戳和字符串，把数据库时间统一转成 LocalDateTime。
    private java.time.LocalDateTime toLocalDateTime(Object value) {
        if(value instanceof java.time.LocalDateTime dateTime) return dateTime;
        if(value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return java.time.LocalDateTime.parse(value.toString().replace(' ','T'));
    }

    // 把 Mapper 返回的一行品类统计转换成清晰的业务对象。
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

    // 把数据库金额统一成两位小数，空值按零金额处理。
    private BigDecimal toAmount(Object amount){
        return amount == null
                ? BigDecimal.ZERO.setScale(2,RoundingMode.HALF_UP)
                : new BigDecimal(amount.toString())
                .setScale(2,RoundingMode.HALF_UP);
    }

    // 统一进入商品排行缓存流程，缓存异常会由 RedisService 内部降级。
    private List<ProductRankingList> getProductRankingList(Filter filter,
                                                           Scope scope){
        return getProductRankingListByRedis(filter,scope);
    }

    // 优先读取两个排行榜缓存，未命中时查询数据库并重建缓存。
    private List<ProductRankingList> getProductRankingListByRedis(Filter filter,
                                                                  Scope scope){
        String cacheKey = getProductRankingCacheKey(filter,scope);
        String amountRedisKey = "product:amount:ranking:" + cacheKey;
        String quantityRedisKey = "product:quantity:ranking:" + cacheKey;

        boolean amountExists = redisService.hasKey(amountRedisKey);
        boolean quantityExists = redisService.hasKey(quantityRedisKey);

        if(amountExists && quantityExists){
            List<ProductRankingList> cacheResult = getProductRankingFromRedis(
                    amountRedisKey,quantityRedisKey
            );
            if(cacheResult != null){
                return cacheResult;
            }
        }

        List<ProductRankingList> productRankingList =
                selectProductRanking(filter,scope);

        redisService.replaceZSet(amountRedisKey, productRankingList.stream()
                        .map(item -> new RedisService.ScoredValue(
                                item.getProduct().getProductId().toString(),
                                item.getSalesAmount().movePointRight(2).doubleValue())).toList(),
                Duration.ofMinutes(50), Duration.ofMinutes(70));
        redisService.replaceZSet(quantityRedisKey, productRankingList.stream()
                        .map(item -> new RedisService.ScoredValue(
                                item.getProduct().getProductId().toString(),
                                item.getSalesVolume().doubleValue())).toList(),
                Duration.ofMinutes(50), Duration.ofMinutes(70));

        return productRankingList;
    }

    // 绕过缓存直接从数据库读取商品排行。
    private List<ProductRankingList> selectProductRanking(Filter filter,
                                                           Scope scope){
        return productMapper.selectProductRanking(filter,scope)
                .stream()
                .map(this::toProductRankingList)
                .toList();
    }

    // 把销售额和销量两个 ZSet 合并回完整的商品排行对象。
    private List<ProductRankingList> getProductRankingFromRedis(
            String amountRedisKey,
            String quantityRedisKey){
        List<RedisService.ScoredValue> amountResult =
                redisService.reverseRangeWithScores(amountRedisKey,0,-1);
        List<RedisService.ScoredValue> quantityResult =
                redisService.reverseRangeWithScores(quantityRedisKey,0,-1);

        if(amountResult == null || quantityResult == null
                || amountResult.size() != quantityResult.size()){
            return null;
        }

        Map<Long,BigDecimal> productAmount = new HashMap<>();
        for(RedisService.ScoredValue item : amountResult){
            productAmount.put(Long.valueOf(item.value()),
                    BigDecimal.valueOf((long)item.score(),2));
        }

        Map<Long,Long> productQuantity = new HashMap<>();
        for(RedisService.ScoredValue item : quantityResult){
            productQuantity.put(Long.valueOf(item.value()), (long)item.score());
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
    // 把 Mapper 查询行转换成商品排行中的一条记录。
    private ProductRankingList toProductRankingList(Map<String,Object> item){
        return ProductRankingList.builder()
                .product(toProductInfo(item))
                .salesAmount(new BigDecimal(item.get("salesAmount").toString())
                        .setScale(2, RoundingMode.HALF_UP))
                .salesVolume(((Number) item.get("salesVolume")).longValue())
                .build();
    }
    // 提取排行榜展示需要的商品基础信息。
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
    // 用权限和筛选条件生成稳定的排行榜缓存 key。
    private String getProductRankingCacheKey(Filter filter,Scope scope){
        return String.join(":",
                getScopeCacheKey(scope),
                filter.getStartTime().toString(),
                filter.getEndTime().toString(),
                String.join("|",filter.getCategories()),
                String.join("|",filter.getBrands())
        );
    }
    // 把账号的数据范围转换成缓存 key 的权限部分。
    private String getScopeCacheKey(Scope scope){
        return scope.getMode() + ":" + String.join("|",scope.getAllowedBrands());
    }


}
