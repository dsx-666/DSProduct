package com.product.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BrandBusinessMapper {
    // 按品牌统计每个用户的历史完成订单数，再汇总新客、老客和异常人数。
    @Select("""
        <script>
        select sum(case when buys.purchaseCount &lt;= 3 and u.user_id is not null then 1 else 0 end) as newBuyerCount,
               sum(case when buys.purchaseCount &gt; 3 and u.user_id is not null then 1 else 0 end) as oldBuyerCount,
               sum(case when u.user_id is not null then 1 else 0 end) as classifiedBuyerCount,
               count(*) as totalBuyerCount,
               sum(case when u.user_id is null then 1 else 0 end) as unclassifiedBuyerCount
        from (
          select o.user_id,count(distinct o.order_id) as purchaseCount
          from `order` o join order_item oi on oi.order_id=o.order_id
               join product p on p.product_id=oi.product_id
          where o.order_status='completed'
            <if test="brand != null">and p.brand=#{brand}</if>
          group by o.user_id
        ) buys
        left join `user` u on u.user_id=buys.user_id
        </script>
        """)
    List<Map<String,Object>> selectBrandCustomerSegments(@Param("brand") String brand);

    // 读取全部真实商品 ID，供布隆过滤器第一次初始化。
    @Select("select product_id from product")
    List<Long> selectAllProductIds();

    // 分页查询商品经营列表，并在一条 SQL 中聚合销量、销售额和复购率。
    @Select("""
        <script>
        select p.product_id as productId, p.product_name as productName, p.brand, p.category,
               p.price as currentPrice, p.rating,
               coalesce(s.salesVolume,0) as salesVolume,
               coalesce(s.salesAmount,0) as salesAmount,
               coalesce(s.buyerCount,0) as buyerCount,
               afterSale.returnRate, afterSale.cancelRate, rep.repurchaseRate,
               coalesce(events.viewCount,0) as viewCount,
               coalesce(events.cartUserCount,0) as cartUserCount,
               coalesce(events.wishlistUserCount,0) as wishlistUserCount
        from product p
        left join (
          select oi.product_id, sum(oi.quantity) as salesVolume,
                 round(sum(round(oi.item_total,2)),2) as salesAmount,
                 count(distinct o.user_id) as buyerCount
          from order_item oi join `order` o on o.order_id=oi.order_id
          where o.order_status='completed'
            <if test="startTime != null">and o.order_date &gt;= #{startTime}</if>
            <if test="endTime != null">and o.order_date &lt; date_add(#{endTime}, interval 1 day)</if>
          group by oi.product_id
        ) s on s.product_id=p.product_id
        left join (
          select purchases.product_id,
                 round(sum(case when purchases.purchaseCount &gt;= 2 then 1 else 0 end)/count(*),6) as repurchaseRate
          from (
            select oi.product_id,o.user_id,count(distinct o.order_id) as purchaseCount
            from order_item oi join `order` o on o.order_id=oi.order_id
            where o.order_status='completed'
              <if test="startTime != null">and o.order_date &gt;= #{startTime}</if>
              <if test="endTime != null">and o.order_date &lt; date_add(#{endTime}, interval 1 day)</if>
            group by oi.product_id,o.user_id
          ) purchases group by purchases.product_id
        ) rep on rep.product_id=p.product_id
        left join (
          select oi.product_id,
                 round(count(distinct case when o.order_status='returned' then o.order_id end)
                       / nullif(count(distinct o.order_id),0),6) as returnRate,
                 round(count(distinct case when o.order_status='cancelled' then o.order_id end)
                       / nullif(count(distinct o.order_id),0),6) as cancelRate
          from order_item oi join `order` o on o.order_id=oi.order_id
          where 1=1
            <if test="startTime != null">and o.order_date &gt;= #{startTime}</if>
            <if test="endTime != null">and o.order_date &lt; date_add(#{endTime}, interval 1 day)</if>
          group by oi.product_id
        ) afterSale on afterSale.product_id=p.product_id
        left join (
          select e.product_id,
                 sum(case when e.event_type='view' then 1 else 0 end) as viewCount,
                 count(distinct case when e.event_type='cart' then e.user_id end) as cartUserCount,
                 count(distinct case when e.event_type='wishlist' then e.user_id end) as wishlistUserCount
          from `event` e
          where 1=1
            <if test="startTime != null">and e.event_timestamp &gt;= #{startTime}</if>
            <if test="endTime != null">and e.event_timestamp &lt; date_add(#{endTime}, interval 1 day)</if>
          group by e.product_id
        ) events on events.product_id=p.product_id
        <where>
          <if test="brands != null and !brands.isEmpty()">
            p.brand in <foreach collection="brands" item="b" open="(" separator="," close=")">#{b}</foreach>
          </if>
          <if test="keyWord != null and keyWord != ''">
            and (cast(p.product_id as char)=#{keyWord} or p.product_name like concat('%',#{keyWord},'%'))
          </if>
        </where>
        order by
          <choose>
            <when test="sortBy == 'salesAmount'">salesAmount</when>
            <when test="sortBy == 'wishlistCount'">wishlistUserCount</when>
            <otherwise>salesVolume</otherwise>
          </choose>
          <choose><when test="order == 'asc'">asc</when><otherwise>desc</otherwise></choose>, p.product_id asc
        limit #{offset},#{num}
        </script>
        """)
    List<Map<String,Object>> selectBrandProducts(@Param("brands") List<String> brands,
            @Param("keyWord") String keyWord, @Param("startTime") LocalDate startTime,
            @Param("endTime") LocalDate endTime, @Param("sortBy") String sortBy,
            @Param("order") String order, @Param("offset") long offset, @Param("num") int num);

    // 统计符合品牌和关键字条件的商品总数。
    @Select("""
        <script>select count(*) from product p <where>
          <if test="brands != null and !brands.isEmpty()">p.brand in
            <foreach collection="brands" item="b" open="(" separator="," close=")">#{b}</foreach>
          </if>
          <if test="keyWord != null and keyWord != ''">and (cast(p.product_id as char)=#{keyWord} or p.product_name like concat('%',#{keyWord},'%'))</if>
        </where></script>
        """)
    long countBrandProducts(@Param("brands") List<String> brands, @Param("keyWord") String keyWord);

    // 按商品 ID 读取最基本的商品信息，用于详情权限确认。
    @Select("select product_id as productId, product_name as productName, brand, category, price as currentPrice, rating from product where product_id=#{productId}")
    Map<String,Object> selectProduct(@Param("productId") long productId);

    // 汇总单个商品的完成订单销量、销售额、买家数和订单数。
    @Select("""
        select coalesce(sum(oi.quantity),0) as salesVolume,
               coalesce(round(sum(round(oi.item_total,2)),2),0) as salesAmount,
               count(distinct o.user_id) as buyerCount,
               count(distinct o.order_id) as orderCount
        from order_item oi
        join `order` o on o.order_id = oi.order_id
        where oi.product_id = #{productId}
          and o.order_status = 'completed'
          and (#{startTime} is null or o.order_date >= #{startTime})
          and (#{endTime} is null or o.order_date < date_add(#{endTime}, interval 1 day))
        """)
    Map<String,Object> selectProductSales(@Param("productId") long productId,
            @Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime);

    // 按当前订单状态计算商品退货率和取消率，没有订单时比例返回 null。
    @Select("""
        select round(count(distinct case when o.order_status='returned' then o.order_id end)
                     / nullif(count(distinct o.order_id),0),6) as returnRate,
               round(count(distinct case when o.order_status='cancelled' then o.order_id end)
                     / nullif(count(distinct o.order_id),0),6) as cancelRate
        from order_item oi join `order` o on o.order_id=oi.order_id
        where oi.product_id=#{productId}
          and (#{startTime} is null or o.order_date >= #{startTime})
          and (#{endTime} is null or o.order_date < date_add(#{endTime}, interval 1 day))
        """)
    Map<String,Object> selectProductAfterSaleRates(@Param("productId") long productId,
            @Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime);

    // 统计浏览事件次数，以及发生过购物车和愿望单事件的去重用户数。
    @Select("""
        select sum(case when event_type='view' then 1 else 0 end) as viewCount,
               count(distinct case when event_type='cart' then user_id end) as cartUserCount,
               count(distinct case when event_type='wishlist' then user_id end) as wishlistUserCount
        from `event`
        where product_id=#{productId}
          and (#{startTime} is null or event_timestamp >= #{startTime})
          and (#{endTime} is null or event_timestamp < date_add(#{endTime}, interval 1 day))
        """)
    Map<String,Object> selectProductEventMetrics(@Param("productId") long productId,
            @Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime);

    // 计算购买两次及以上用户占全部购买用户的比例。
    @Select("""
        select case when count(*)=0 then null
                    else round(sum(case when purchaseCount >= 2 then 1 else 0 end)/count(*),6) end
        from (
          select o.user_id,count(distinct o.order_id) as purchaseCount
          from order_item oi join `order` o on o.order_id=oi.order_id
          where oi.product_id=#{productId} and o.order_status='completed'
            and (#{startTime} is null or o.order_date >= #{startTime})
            and (#{endTime} is null or o.order_date < date_add(#{endTime}, interval 1 day))
          group by o.user_id
        ) purchases
        """)
    BigDecimal selectRepurchaseRate(@Param("productId") long productId,
            @Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime);

    // 按接口约定的性别类型统计商品购买用户分布。
    @Select("""
        SELECT
            gender,
            COUNT(DISTINCT userId) AS buyerCount
        FROM (
            SELECT
                o.user_id AS userId,
                CASE
                    WHEN u.user_id IS NULL THEN 'UNKNOWN'
                    WHEN UPPER(u.gender) IN ('MALE', 'M') THEN 'Male'
                    WHEN UPPER(u.gender) IN ('FEMALE', 'F') THEN 'Female'
                    WHEN u.gender IS NULL OR TRIM(u.gender) = '' THEN 'UNKNOWN'
                    ELSE 'Other'
                END AS gender
            FROM order_item oi
            JOIN `order` o ON o.order_id = oi.order_id
            LEFT JOIN `user` u ON u.user_id = o.user_id
            WHERE oi.product_id = #{productId}
              AND o.order_status = 'completed'
              AND (#{startTime} IS NULL OR o.order_date >= #{startTime})
              AND (#{endTime} IS NULL OR o.order_date < DATE_ADD(#{endTime}, INTERVAL 1 DAY))
        ) t
        GROUP BY gender
        """)
    List<Map<String,Object>> selectProductGender(@Param("productId") long productId,
            @Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime);

    // 按固定业务日和注册日期划分商品的新客与老客。
    @Select("""
        select case when u.user_id is null or u.signup_date is null or u.signup_date > #{businessDate} then 'UNKNOWN'
                    when datediff(#{businessDate},u.signup_date) < 30 then 'NEW' else 'OLD' end as customerType,
               count(distinct o.user_id) as buyerCount
        from order_item oi join `order` o on o.order_id=oi.order_id
        left join `user` u on u.user_id=o.user_id
        where oi.product_id=#{productId} and o.order_status='completed'
          and (#{startTime} is null or o.order_date >= #{startTime})
          and (#{endTime} is null or o.order_date < date_add(#{endTime}, interval 1 day))
        group by customerType
        """)
    List<Map<String,Object>> selectProductCustomerSegments(@Param("productId") long productId,
            @Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime,
            @Param("businessDate") LocalDate businessDate);

    // 统计商品评价数量和平均评分。
    @Select("select count(*) as reviewCount, avg(rating) as reviewAverageRating from review where product_id=#{productId} and (#{startTime} is null or review_date >= #{startTime}) and (#{endTime} is null or review_date < date_add(#{endTime}, interval 1 day))")
    Map<String,Object> selectReviewStats(@Param("productId") long productId,
            @Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime);

    // 读取商品详情首屏需要展示的最近几条评价。
    @Select("select review_id as reviewId, rating, review_text as reviewText, review_date as reviewDate from review where product_id=#{productId} and (#{startTime} is null or review_date >= #{startTime}) and (#{endTime} is null or review_date < date_add(#{endTime}, interval 1 day)) order by review_date desc, review_id asc limit #{offset},#{num}")
    List<Map<String,Object>> selectReviewPreview(@Param("productId") long productId,
            @Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime,
            @Param("offset") long offset, @Param("num") int num);

    // 统计评价分页在当前筛选条件下的总记录数。
    @Select("<script>select count(*) from review where product_id=#{productId} <if test='startTime != null'>and review_date &gt;= #{startTime}</if> <if test='endTime != null'>and review_date &lt; date_add(#{endTime}, interval 1 day)</if> <if test='rating != null'>and rating=#{rating}</if></script>")
    long countReviews(@Param("productId") long productId, @Param("startTime") LocalDate startTime,
            @Param("endTime") LocalDate endTime, @Param("rating") Integer rating);

    // 分页读取商品评价以及用于脱敏展示的买家最小信息。
    @Select("<script>select r.review_id as reviewId,r.rating,r.review_text as reviewText,r.review_date as reviewDate,u.user_id as buyerId,u.name as buyerName from review r left join `user` u on u.user_id=r.user_id where r.product_id=#{productId} <if test='startTime != null'>and r.review_date &gt;= #{startTime}</if> <if test='endTime != null'>and r.review_date &lt; date_add(#{endTime}, interval 1 day)</if> <if test='rating != null'>and r.rating=#{rating}</if> order by r.review_date desc,r.review_id asc limit #{offset},#{num}</script>")
    List<Map<String,Object>> selectReviews(@Param("productId") long productId,
            @Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime,
            @Param("rating") Integer rating, @Param("offset") long offset, @Param("num") int num);

    // 分页读取去重买家，并计算每个人的购买订单数和最近购买时间。
    @Select("<script>select o.user_id as userId,u.name as displayName,u.gender,u.signup_date as signupDate,count(distinct o.order_id) as purchaseOrderCount,max(o.order_date) as lastPurchaseAt from order_item oi join `order` o on o.order_id=oi.order_id left join `user` u on u.user_id=o.user_id where oi.product_id=#{productId} and o.order_status='completed' and (#{startTime} is null or o.order_date &gt;= #{startTime}) and (#{endTime} is null or o.order_date &lt; date_add(#{endTime}, interval 1 day)) <if test='userId != null'>and o.user_id=#{userId}</if> group by o.user_id,u.name,u.gender,u.signup_date order by lastPurchaseAt desc,o.user_id asc limit #{offset},#{num}</script>")
    List<Map<String,Object>> selectBuyers(@Param("productId") long productId,
            @Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime,
            @Param("userId") Long userId, @Param("offset") long offset, @Param("num") int num);

    // 统计当前商品筛选条件下的去重买家总数。
    @Select("<script>select count(*) from (select o.user_id from order_item oi join `order` o on o.order_id=oi.order_id where oi.product_id=#{productId} and o.order_status='completed' and (#{startTime} is null or o.order_date &gt;= #{startTime}) and (#{endTime} is null or o.order_date &lt; date_add(#{endTime}, interval 1 day)) <if test='userId != null'>and o.user_id=#{userId}</if> group by o.user_id) buyers</script>")
    long countBuyers(@Param("productId") long productId, @Param("startTime") LocalDate startTime,
            @Param("endTime") LocalDate endTime, @Param("userId") Long userId);

}
