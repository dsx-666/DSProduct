package com.product.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.product.MyException.ApiHttpException;
import com.product.MyException.BusinessException;
import com.product.config.BusinessProperties;
import com.product.converter.UserConverter;
import com.product.enums.Code;
import com.product.mapper.BrandBusinessMapper;
import com.product.mapper.ProductMapper;
import com.product.pojo.bo.Scope;
import com.product.pojo.po.User;
import com.product.service.BrandBusinessService;
import com.product.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class BrandBusinessServiceImpl implements BrandBusinessService {
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2025, 11, 14);
    private final BrandBusinessMapper mapper;
    private final ProductMapper productMapper;
    private final BusinessProperties businessProperties;
    private final RedisService redisService;

    // 统计品牌历史购买次数的新老客分层，缓存只负责提速，最终数据仍来自数据库。
    @Override
    public Map<String,Object> getCustomerSegments(User user, String requestedBrand) {
        Scope scope = UserConverter.toScope(user);
        String brand = resolveBrand(scope, requestedBrand, true);
        String cacheKey = "brand:segments:" + (brand == null ? "ALL" : brand);
        Map<String,Object> data = redisService.getOrLoadWithMutex(
                cacheKey, Duration.ofSeconds(300), Duration.ofSeconds(600),
                new TypeReference<Map<String,Object>>() {}, () -> {
            List<Map<String,Object>> rows = mapper.selectBrandCustomerSegments(brand);
            long fresh = 0, old = 0, classified = 0, total = 0, unknown = 0;
            for(Map<String,Object> row : rows) {
                fresh += number(row.get("newBuyerCount"));
                old += number(row.get("oldBuyerCount"));
                classified += number(row.get("classifiedBuyerCount"));
                total += number(row.get("totalBuyerCount"));
                unknown += number(row.get("unclassifiedBuyerCount"));
            }
            long allClassified = classified;
            return mapOf("filters", mapOf("brand", brand == null ? List.of() : List.of(brand)),
                    "period", "LIFETIME", "newUserPurchaseThreshold", 3,
                    "newBuyerCount", fresh, "oldBuyerCount", old,
                    "classifiedBuyerCount", classified, "totalBuyerCount", total,
                    "newBuyerRatio", ratio(fresh, allClassified),
                    "oldBuyerRatio", ratio(old, allClassified),
                    "unclassifiedBuyerCount", unknown,
                    "unclassifiedRatio", ratio(unknown, total),
                    "generatedAt", java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime());
        });
        Scope effectiveScope = scope;
        if("ALL".equals(scope.getMode()) && brand != null)
            effectiveScope = Scope.builder().mode("BRAND").allowedBrands(List.of(brand))
                    .canViewFullOrder(true).canManageUsers(true).build();
        data.put("scope", scopeMap(effectiveScope));
        return data;
    }

    // 按当前账号能看的品牌范围分页查询商品经营指标。
    @Override
    public Map<String,Object> getProducts(User user, int page, int num, String range,
            LocalDate startTime, LocalDate endTime, List<String> requestedBrands,
            String keyWord, String sortBy, String order) {
        if(page < 1 || num < 1 || num > 100) badRequest("分页参数错误");
        if(range == null || !List.of("FILTERED", "ALL").contains(range)) badRequest("range参数错误");
        if(sortBy == null || !List.of("salesVolume", "salesAmount", "wishlistCount").contains(sortBy)) badRequest("sortBy参数错误");
        if(order == null || !List.of("asc", "desc").contains(order)) badRequest("order参数错误");
        if(keyWord != null) {
            keyWord = keyWord.trim();
            if(keyWord.length() > 100) badRequest("keyWord长度不能超过100");
            if(keyWord.isEmpty()) keyWord = null;
        }
        LocalDate[] dates = resolveRange(range, startTime, endTime);
        Scope scope = UserConverter.toScope(user);
        List<String> brands = resolveProductBrands(scope, requestedBrands);
        if("BRAND".equals(scope.getMode()) && brands.isEmpty())
            return mapOf("filters", mapOf("range", range, "startTime", dates[0], "endTime", dates[1],
                            "brand", List.of(), "keyWord", keyWord, "sortBy", sortBy, "order", order),
                    "scope", scopeMap(scope), "total", 0L, "pageNum", page,
                    "pageSize", num, "productList", List.of());
        long offset = offset(page, num);
        List<Map<String,Object>> products = mapper.selectBrandProducts(brands, keyWord,
                dates[0], dates[1], sortBy, order, offset, num);
        products.forEach(p -> {
            p.put("metricStatus", metricStatus());
        });
        Map<String,Object> filter = mapOf("range", range, "startTime", dates[0], "endTime", dates[1],
                "brand", brands == null ? List.of() : brands, "keyWord", keyWord,
                "sortBy", sortBy, "order", order);
        return mapOf("filters", filter, "scope", scopeMap(scope),
                "total", mapper.countBrandProducts(brands, keyWord), "pageNum", page,
                "pageSize", num, "productList", products);
    }

    // 查询单个商品的完整经营详情，并通过互斥缓存减少重复聚合查询。
    @Override
    public Map<String,Object> getProductDetail(User user, long productId, String range,
            LocalDate startTime, LocalDate endTime) {
        if(productId <= 0) badRequest("productId必须为正整数");
        LocalDate[] dates = resolveRange(range, startTime, endTime);
        Scope scope = UserConverter.toScope(user);
        Map<String,Object> product = getProductOrThrow(productId);
        authorizeProduct(product, scope);
        String key = "brand:product:detail:v2:" + productId + ":" + range + ":" + dates[0] + ":" + dates[1];
        Map<String,Object> data = redisService.getOrLoadWithMutex(
                key, Duration.ofSeconds(180), Duration.ofSeconds(300),
                new TypeReference<Map<String,Object>>() {},
                () -> loadProductDetail(product, scope, range, dates[0], dates[1]));
        data.put("scope", scopeMap(scope));
        return data;
    }

    // 真正从数据库组装商品详情，包括销量、购买用户、评价和人群结构。
    private Map<String,Object> loadProductDetail(Map<String,Object> product, Scope scope,
            String range, LocalDate startTime, LocalDate endTime) {
        long productId = number(product.get("productId"));
        Map<String,Object> sales = mapper.selectProductSales(productId, startTime, endTime);
        Map<String,Object> afterSale = mapper.selectProductAfterSaleRates(
                productId, startTime, endTime);
        Map<String,Object> events = mapper.selectProductEventMetrics(
                productId, startTime, endTime);
        Map<String,Object> reviewStats = mapper.selectReviewStats(productId, startTime, endTime);
        BigDecimal repurchaseRate = mapper.selectRepurchaseRate(productId, startTime, endTime);
        List<Map<String,Object>> genderRows = mapper.selectProductGender(productId, startTime, endTime);
        List<Map<String,Object>> customerRows = mapper.selectProductCustomerSegments(
                productId, startTime, endTime, BUSINESS_DATE);
        long buyerCount = number(sales.get("buyerCount"));
        Map<String,Long> genders = new HashMap<>();
        for(Map<String,Object> row : genderRows) genders.put(String.valueOf(row.get("gender")), number(row.get("buyerCount")));
        List<Map<String,Object>> genderDistribution = new ArrayList<>();
        for(String gender : List.of("Male", "Female", "Other", "UNKNOWN"))
            genderDistribution.add(mapOf("gender", gender, "buyerCount", genders.getOrDefault(gender, 0L),
                    "ratio", ratio(genders.getOrDefault(gender, 0L), buyerCount)));
        Map<String,Long> segments = new HashMap<>();
        for(Map<String,Object> row : customerRows) segments.put(String.valueOf(row.get("customerType")), number(row.get("buyerCount")));
        long fresh = segments.getOrDefault("NEW", 0L), old = segments.getOrDefault("OLD", 0L);
        long unknown = segments.getOrDefault("UNKNOWN", 0L), classified = fresh + old;
        List<Map<String,Object>> preview = mapper.selectReviewPreview(productId, startTime, endTime, 0, 5);
        long reviewCount = number(reviewStats.get("reviewCount"));
        Object reviewAverage = reviewStats.get("reviewAverageRating");
        Map<String,Object> metrics = mapOf("salesVolume", number(sales.get("salesVolume")),
                "salesAmount", amount(sales.get("salesAmount")), "buyerCount", buyerCount,
                "returnRate", decimal(value(afterSale, "returnRate")),
                "cancelRate", decimal(value(afterSale, "cancelRate")),
                "repurchaseRate", repurchaseRate,
                "viewCount", number(value(events, "viewCount")),
                "cartUserCount", number(value(events, "cartUserCount")),
                "wishlistUserCount", number(value(events, "wishlistUserCount")),
                "purchasingUserCount", buyerCount, "reviewCount", reviewCount,
                "reviewAverageRating", reviewAverage == null ? null : amount(reviewAverage),
                "rating", product.get("rating"));
        return mapOf("filters", mapOf("range", range, "startTime", startTime, "endTime", endTime),
                "scope", scopeMap(scope), "product", product, "metrics", metrics,
                "genderDistribution", genderDistribution,
                "customerSegments", mapOf("newBuyerCount", fresh, "oldBuyerCount", old,
                        "classifiedBuyerCount", classified, "unclassifiedBuyerCount", unknown,
                        "newBuyerRatio", ratio(fresh, classified), "oldBuyerRatio", ratio(old, classified)),
                "reviewPreview", preview, "metricStatus", metricStatus());
    }

    // 分页查询商品评价，只返回经过脱敏的最小买家信息。
    @Override
    public Map<String,Object> getReviews(User user, long productId, int page, int num,
            LocalDate startTime, LocalDate endTime, Integer rating) {
        validateProductAndRange(user, productId, "ALL", null, null);
        if(page < 1 || num < 1 || num > 100) badRequest("分页参数错误");
        validateDatePair(startTime, endTime);
        if(rating != null && (rating < 1 || rating > 5)) badRequest("rating必须为1到5");
        long offset = offset(page, num);
        List<Map<String,Object>> reviews = mapper.selectReviews(productId, startTime, endTime, rating, offset, num);
        for(Map<String,Object> review : reviews) {
            Object buyerId = review.remove("buyerId");
            Object buyerName = review.remove("buyerName");
            review.put("buyer", buyerId == null ? null : mapOf("buyerId", buyerId,
                    "displayName", maskName(Objects.toString(buyerName, ""))));
        }
        return mapOf("productId", productId, "total", mapper.countReviews(productId, startTime, endTime, rating),
                "pageNum", page, "pageSize", num, "reviewList", reviews);
    }

    // 分页查询去重购买用户，并补齐性别和新老客标签。
    @Override
    public Map<String,Object> getBuyers(User user, long productId, int page, int num,
            String range, LocalDate startTime, LocalDate endTime, String keyWord) {
        if(page < 1 || num < 1 || num > 100) badRequest("分页参数错误");
        if(keyWord != null) {
            keyWord = keyWord.trim();
            if(keyWord.isEmpty()) keyWord = null;
        }
        Long userId = null;
        if(keyWord != null) {
            try { userId = Long.valueOf(keyWord); if(userId <= 0) badRequest("keyWord必须为正整数userId"); }
            catch(NumberFormatException e) { badRequest("keyWord仅支持userId精确匹配"); }
        }
        LocalDate[] dates = validateProductAndRange(user, productId, range, startTime, endTime);
        long offset = offset(page, num);
        List<Map<String,Object>> buyers = mapper.selectBuyers(productId, dates[0], dates[1], userId, offset, num);
        for(Map<String,Object> buyer : buyers) {
            buyer.put("displayName", maskName(Objects.toString(buyer.get("displayName"), "")));
            buyer.put("gender", normalizeGender(buyer.get("gender")));
            Object signup = buyer.get("signupDate");
            LocalDate signupDate = signup == null ? null : toDate(signup);
            buyer.put("customerType", signupDate == null || signupDate.isAfter(BUSINESS_DATE) ? "UNKNOWN"
                    : java.time.temporal.ChronoUnit.DAYS.between(signupDate, BUSINESS_DATE) < 30 ? "NEW" : "OLD");
        }
        return mapOf("productId", productId,
                "filters", mapOf("range", range, "startTime", dates[0], "endTime", dates[1], "keyWord", keyWord),
                "scope", scopeMap(UserConverter.toScope(user)),
                "total", mapper.countBuyers(productId, dates[0], dates[1], userId),
                "pageNum", page, "pageSize", num, "buyerList", buyers);
    }

    // 在查商品相关数据前统一校验商品、权限和日期范围。
    private LocalDate[] validateProductAndRange(User user, long productId, String range,
            LocalDate startTime, LocalDate endTime) {
        if(productId <= 0) badRequest("productId必须为正整数");
        Scope scope = UserConverter.toScope(user);
        authorizeProduct(getProductOrThrow(productId), scope);
        return resolveRange(range, startTime, endTime);
    }

    // 先用布隆过滤器和空值缓存拦截不存在的商品，再回数据库做最终确认。
    private Map<String,Object> getProductOrThrow(long productId) {
        redisService.initializeBloomFilter("product", mapper::selectAllProductIds);
        if(!redisService.mightContain("product", productId))
            throw new ApiHttpException(HttpStatus.NOT_FOUND, 404, "商品不存在");
        String emptyKey = "brand:product:empty:" + productId;
        if(redisService.hasCachedNull(emptyKey))
            throw new ApiHttpException(HttpStatus.NOT_FOUND, 404, "商品不存在");
        Map<String,Object> product = mapper.selectProduct(productId);
        if(product != null) return product;
        redisService.cacheNull(emptyKey, Duration.ofSeconds(30), Duration.ofSeconds(60));
        throw new ApiHttpException(HttpStatus.NOT_FOUND, 404, "商品不存在");
    }

    // 检查当前账号是否能看这个商品，管理员越权时也统一按商品不存在处理。
    private void authorizeProduct(Map<String,Object> product, Scope scope) {
        if(product == null) throw new ApiHttpException(HttpStatus.NOT_FOUND, 404, "商品不存在");
        if("BRAND".equals(scope.getMode()) && (scope.getAllowedBrands() == null
                || !scope.getAllowedBrands().contains(String.valueOf(product.get("brand")))))
            throw new ApiHttpException(HttpStatus.NOT_FOUND, 404, "商品不存在");
    }

    // 把 ALL 或 FILTERED 转成实际日期，顺便执行经营日期边界校验。
    private LocalDate[] resolveRange(String range, LocalDate startTime, LocalDate endTime) {
        if(range == null || !List.of("FILTERED", "ALL").contains(range)) badRequest("range参数错误");
        if("ALL".equals(range)) {
            if(startTime != null || endTime != null) badRequest("range=ALL时不能传日期");
            return new LocalDate[]{null, null};
        }
        LocalDate effectiveEnd = endTime == null ? businessProperties.getTime() : endTime;
        LocalDate effectiveStart = startTime == null ? effectiveEnd.minusDays(29) : startTime;
        if(startTime == null && effectiveStart.isBefore(businessProperties.getStartTime()))
            effectiveStart = businessProperties.getStartTime();
        validateDatePair(effectiveStart, effectiveEnd);
        if(effectiveStart.isBefore(businessProperties.getStartTime()) || effectiveEnd.isAfter(businessProperties.getTime()))
            badRequest("日期超出允许范围");
        return new LocalDate[]{effectiveStart, effectiveEnd};
    }

    // 保证开始日期不晚于结束日期。
    private void validateDatePair(LocalDate start, LocalDate end) {
        if(start != null && end != null && start.isAfter(end)) badRequest("开始日期不能晚于结束日期");
    }

    // 根据登录角色确定 B8 实际查询的品牌，客户端不能扩大自己的权限。
    private String resolveBrand(Scope scope, String requestedBrand, boolean strictMissing) {
        if("BRAND".equals(scope.getMode())) {
            if(requestedBrand != null) badRequest("ADMIN不能传brand参数");
            if(scope.getAllowedBrands() == null || scope.getAllowedBrands().isEmpty()) {
                if(strictMissing) throw new ApiHttpException(HttpStatus.FORBIDDEN, 403, "当前账号未绑定品牌");
                return null;
            }
            return scope.getAllowedBrands().get(0);
        }
        if(requestedBrand != null) {
            String brand = requestedBrand.trim();
            if(brand.isEmpty() || brand.contains(",") || brand.equals("all") || brand.equals("null")) badRequest("brand参数错误");
            boolean found = productMapper.selectBrands().stream().filter(Objects::nonNull)
                    .anyMatch(value -> brand.equals(value.toString()));
            if(!found) badRequest("品牌不存在");
            return brand;
        }
        return null;
    }

    // 整理商品列表的品牌条件，并确认请求中的品牌都是真实品牌。
    private List<String> resolveProductBrands(Scope scope, List<String> requested) {
        if("BRAND".equals(scope.getMode())) {
            if(requested != null && !requested.isEmpty()) badRequest("ADMIN不能传brand参数");
            return scope.getAllowedBrands() == null ? List.of() : scope.getAllowedBrands();
        }
        if(requested == null || requested.isEmpty()) return null;
        List<String> selected = requested.stream().map(String::trim).distinct().sorted().toList();
        if(selected.stream().anyMatch(String::isEmpty) || selected.stream().anyMatch(v -> v.contains(",") || v.equals("all") || v.equals("null")))
            badRequest("brand参数错误");
        Set<String> known = new HashSet<>();
        productMapper.selectBrands().stream().filter(Objects::nonNull).forEach(v -> known.add(v.toString()));
        if(!known.containsAll(selected)) badRequest("品牌不存在");
        return selected;
    }

    // 这些指标现在都有真实订单或事件来源，可以明确告诉前端数据可用。
    private Map<String,Object> metricStatus() {
        Map<String,Object> status = new LinkedHashMap<>();
        for(String metric : List.of("returnRate", "cancelRate", "repurchaseRate",
                "viewCount", "cartUserCount", "wishlistUserCount"))
            status.put(metric, "AVAILABLE");
        return status;
    }

    // 把内部权限对象整理成接口文档约定的返回结构。
    private Map<String,Object> scopeMap(Scope scope) {
        return mapOf("mode", scope.getMode(), "allowedBrands", scope.getAllowedBrands(),
                "canViewFullOrder", scope.getCanViewFullOrder(), "canManageUsers", scope.getCanManageUsers());
    }

    // 对用户名做最小化展示，避免接口直接暴露完整姓名。
    private String maskName(String name) {
        if(name == null || name.isBlank()) return null;
        if(name.length() == 1) return "*";
        return name.substring(0, 1) + "*".repeat(Math.min(name.length() - 1, 3));
    }

    // 把数据库里的多种性别写法统一成接口约定的四类。
    private String normalizeGender(Object value) {
        if(value == null || value.toString().isBlank()) return "UNKNOWN";
        String gender = value.toString().trim().toUpperCase(Locale.ROOT);
        if(List.of("MALE", "M").contains(gender)) return "Male";
        if(List.of("FEMALE", "F").contains(gender)) return "Female";
        return "Other";
    }

    // 兼容 JDBC 日期对象和字符串，把它们统一转成 LocalDate。
    private LocalDate toDate(Object value) {
        if(value instanceof LocalDate d) return d;
        if(value instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    // 安全计算分页偏移量，数值溢出时按参数错误处理。
    private long offset(int page, int num) {
        try { return Math.multiplyExact((long) page - 1, num); }
        catch(ArithmeticException e) { badRequest("页码超出范围"); return 0; }
    }

    // 把数据库聚合结果安全转成长整数，空值按零处理。
    private long number(Object value) { return value == null ? 0 : ((Number)value).longValue(); }
    // Mapper 没有返回聚合行时也安全取值，交给后续转换决定返回零还是 null。
    private Object value(Map<String,Object> values, String key) {
        return values == null ? null : values.get(key);
    }
    // 把数据库金额统一成保留两位小数的 BigDecimal。
    private BigDecimal amount(Object value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
    // 比例没有有效分母时保留 null，有值时统一保留六位小数。
    private BigDecimal decimal(Object value) {
        return value == null ? null
                : new BigDecimal(value.toString()).setScale(6, RoundingMode.HALF_UP);
    }
    // 计算最多六位小数的比例，分母为零时返回 null。
    private BigDecimal ratio(long numerator, long denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }
    // 用成对的 key 和 value 快速构造保持顺序的返回对象。
    private Map<String,Object> mapOf(Object... values) {
        Map<String,Object> result = new LinkedHashMap<>();
        for(int i=0; i<values.length; i+=2) result.put((String)values[i], values[i+1]);
        return result;
    }
    // 统一抛出 HTTP 400 的参数错误，避免每个方法重复创建异常。
    private void badRequest(String message) {
        throw new ApiHttpException(HttpStatus.BAD_REQUEST, Code.ParamError.getCode(), message);
    }
}
