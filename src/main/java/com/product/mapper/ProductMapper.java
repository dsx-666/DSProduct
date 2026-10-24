package com.product.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.po.Product;
import com.product.pojo.bo.Scope;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ProductMapper extends BaseMapper<Product> {
    @Select("""
        <script>
        select p.product_id as productId, p.product_name as productName,
               p.brand as brand, p.category as category,
               count(r.review_id) as reviewCount,
               avg(r.rating) as averageRating,
               sum(case when r.rating=1 then 1 else 0 end) as rating1,
               sum(case when r.rating=2 then 1 else 0 end) as rating2,
               sum(case when r.rating=3 then 1 else 0 end) as rating3,
               sum(case when r.rating=4 then 1 else 0 end) as rating4,
               sum(case when r.rating=5 then 1 else 0 end) as rating5,
               max(r.review_date) as latestReviewDate
        from product p left join review r on r.product_id=p.product_id
          and (#{startTime} is null or r.review_date &gt;= #{startTime})
          and (#{endTime} is null or r.review_date &lt; DATE_ADD(#{endTime}, INTERVAL 1 DAY))
        <where>
          <if test="categories != null and !categories.isEmpty()">AND p.category in
            <foreach collection="categories" item="v" open="(" separator="," close=")">#{v}</foreach>
          </if>
          <if test="brands != null and !brands.isEmpty()">AND p.brand in
            <foreach collection="brands" item="v" open="(" separator="," close=")">#{v}</foreach>
          </if>
          <if test="productId != null">AND p.product_id=#{productId}</if>
        </where>
        group by p.product_id,p.product_name,p.brand,p.category
        order by
          <choose>
            <when test="sort == 'averageRating'">averageRating desc</when>
            <when test="sort == 'latestReview'">latestReviewDate desc</when>
            <otherwise>reviewCount desc</otherwise>
          </choose>, p.product_id asc
        limit #{offset},#{num}
        </script>
    """)
    List<Map<String,Object>> selectReviewInsightProducts(
            @Param("startTime") java.time.LocalDate startTime,
            @Param("endTime") java.time.LocalDate endTime,
            @Param("categories") List<String> categories,
            @Param("brands") List<String> brands,
            @Param("productId") Long productId,
            @Param("sort") String sort,
            @Param("offset") Long offset,
            @Param("num") Long num);

    @Select("""
        <script>
        select count(*) from product p
        <where>
          <if test="categories != null and !categories.isEmpty()">AND p.category in
            <foreach collection="categories" item="v" open="(" separator="," close=")">#{v}</foreach>
          </if>
          <if test="brands != null and !brands.isEmpty()">AND p.brand in
            <foreach collection="brands" item="v" open="(" separator="," close=")">#{v}</foreach>
          </if>
          <if test="productId != null">AND p.product_id=#{productId}</if>
        </where>
        </script>
    """)
    Long countReviewInsightProducts(@Param("categories") List<String> categories,
                                    @Param("brands") List<String> brands,
                                    @Param("productId") Long productId);

    @Select("""
        <script>
        select product_id as productId,rating,review_text as reviewText,review_date as reviewDate
        from review where product_id in
          <foreach collection="productIds" item="id" open="(" separator="," close=")">#{id}</foreach>
          and (#{startTime} is null or review_date &gt;= #{startTime})
          and (#{endTime} is null or review_date &lt; DATE_ADD(#{endTime}, INTERVAL 1 DAY))
        order by product_id asc,review_date desc,review_id asc
        </script>
    """)
    List<Map<String,Object>> selectInsightReviewRows(@Param("productIds") List<Long> productIds,
                                                      @Param("startTime") java.time.LocalDate startTime,
                                                      @Param("endTime") java.time.LocalDate endTime);

    @Select("""
        <script>
        select case when u.user_id is null or u.signup_date is null
                    or u.signup_date &gt; #{businessDate} then 'UNCLASSIFIED'
                    when DATEDIFF(#{businessDate},u.signup_date) &lt; 30 then 'NEW'
                    else 'OLD' end as segment,
               p.product_id as productId,p.product_name as productName,
               p.brand as brand,p.category as category,
               ROUND(SUM(ROUND(oi.item_total,2)),2) as salesAmount,
               SUM(oi.quantity) as quantity,
               COUNT(DISTINCT o.user_id) as buyerCount,
               COUNT(DISTINCT o.order_id) as orderCount
        from `order` o join order_item oi on oi.order_id=o.order_id
          join product p on p.product_id=oi.product_id
          left join `user` u on u.user_id=o.user_id
        where o.order_status='completed'
          and o.order_date &gt;= #{startTime}
          and o.order_date &lt; DATE_ADD(#{endTime},INTERVAL 1 DAY)
          <if test="categories != null and !categories.isEmpty()">AND p.category in
            <foreach collection="categories" item="v" open="(" separator="," close=")">#{v}</foreach>
          </if>
          <if test="brands != null and !brands.isEmpty()">AND p.brand in
            <foreach collection="brands" item="v" open="(" separator="," close=")">#{v}</foreach>
          </if>
          <if test="allowedBrands != null">AND p.brand in
            <foreach collection="allowedBrands" item="v" open="(" separator="," close=")">#{v}</foreach>
          </if>
        group by segment,p.product_id,p.product_name,p.brand,p.category
        </script>
    """)
    List<Map<String,Object>> selectCustomerSalesRankingRows(
            @Param("startTime") java.time.LocalDate startTime,
            @Param("endTime") java.time.LocalDate endTime,
            @Param("businessDate") java.time.LocalDate businessDate,
            @Param("categories") List<String> categories,
            @Param("brands") List<String> brands,
            @Param("allowedBrands") List<String> allowedBrands);

    @Select("""
        <script>
        select case when u.user_id is null or u.signup_date is null or u.signup_date &gt; #{businessDate} then 'UNCLASSIFIED'
          when DATEDIFF(#{businessDate},u.signup_date) &lt; 30 then 'NEW' else 'OLD' end as segment,
          count(distinct o.user_id) as buyerCount,count(distinct o.order_id) as orderCount,
          ROUND(SUM(ROUND(oi.item_total,2)),2) as salesAmount,SUM(oi.quantity) as quantity
        from `order` o join order_item oi on oi.order_id=o.order_id join product p on p.product_id=oi.product_id
          left join `user` u on u.user_id=o.user_id
        where o.order_status='completed' and o.order_date &gt;= #{startTime} and o.order_date &lt; DATE_ADD(#{endTime},INTERVAL 1 DAY)
          <if test="categories != null and !categories.isEmpty()">AND p.category in <foreach collection="categories" item="v" open="(" separator="," close=")">#{v}</foreach></if>
          <if test="brands != null and !brands.isEmpty()">AND p.brand in <foreach collection="brands" item="v" open="(" separator="," close=")">#{v}</foreach></if>
          <if test="allowedBrands != null"><choose><when test="allowedBrands.isEmpty()">AND 1=0</when><otherwise>AND p.brand in <foreach collection="allowedBrands" item="v" open="(" separator="," close=")">#{v}</foreach></otherwise></choose></if>
        group by segment
        </script>
    """)
    List<Map<String,Object>> selectCustomerSalesSegmentTotals(@Param("startTime") java.time.LocalDate startTime,
            @Param("endTime") java.time.LocalDate endTime,@Param("businessDate") java.time.LocalDate businessDate,
            @Param("categories") List<String> categories,@Param("brands") List<String> brands,
            @Param("allowedBrands") List<String> allowedBrands);

//    @Insert("${a}")
    default List<Object> selectCategories() {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Product::getCategory)
                .groupBy(Product::getCategory);
        return this.selectObjs(wrapper);
    }

    default List<Object> selectBrands() {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Product::getBrand)
                .groupBy(Product::getBrand);
        return this.selectObjs(wrapper);
    }


    @Select("""
        <script>
            select p.product_id
            from `order` o left join `user` u on o.user_id = u.user_id
                left join order_item oi on o.order_id = oi.order_id
                left join product p on p.product_id = oi.product_id
            <where>
                o.order_date &gt;= #{startTime}
                AND o.order_date &lt; DATE_ADD(#{endTime}, INTERVAL 1 DAY)
                <if test="statuses != null and !statuses.isEmpty()">
                    AND o.order_status in
                    <foreach
                        collection="statuses"
                        item="status"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{status}
                    </foreach>
                </if>
                <if test="categories != null and !categories.isEmpty()">
                    AND p.category in
                    <foreach
                        collection="categories"
                        item="category"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{category}
                    </foreach>
                </if>
                <if test="brands != null and !brands.isEmpty()">
                    AND p.brand in
                    <foreach
                        collection="brands"
                        item="brand"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{brand}
                    </foreach>
                </if>
            </where>
            group by p.product_id
            ORDER BY
                p.product_id DESC
        </script>
    """)
    List<Object> selectProductId(Filter filter);

    @Select("""
    <script>
        select p.product_id as productId,
               sum(oi.quantity) as productCount
        from product p inner join order_item oi on p.product_id = oi.product_id
            inner join `order` o on oi.order_id = o.order_id
        <where>
            o.order_status = 'completed'
            <if test="allowedBrands != null and !allowedBrands.isEmpty()">
                    AND p.brand in
                    <foreach
                        collection="allowedBrands"
                        item="brand"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{brand}
                    </foreach>
                </if>
        </where>
        group by p.product_id
        having sum(oi.quantity) &gt; 0
        order by productCount desc
    </script>
    """)
    List<Map<String,Object>> selectAllProductCount(Scope scope);

    @Select("""
        <script>
            select p.product_id as productId,
                   p.product_name as productName,
                   p.category as category,
                   p.brand as brand,
                   ROUND(SUM(ROUND(oi.item_total, 2)), 2) as salesAmount,
                   SUM(oi.quantity) as salesVolume
            from `order` o inner join order_item oi on o.order_id = oi.order_id
                inner join product p on p.product_id = oi.product_id
            <where>
                o.order_status = 'completed'
                AND o.order_date &gt;= #{filter.startTime}
                AND o.order_date &lt; DATE_ADD(#{filter.endTime}, INTERVAL 1 DAY)
                <if test="filter.categories != null and !filter.categories.isEmpty()">
                    AND p.category in
                    <foreach
                        collection="filter.categories"
                        item="category"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{category}
                    </foreach>
                </if>
                <if test="filter.brands != null and !filter.brands.isEmpty()">
                    AND p.brand in
                    <foreach
                        collection="filter.brands"
                        item="brand"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{brand}
                    </foreach>
                </if>
                <if test="scope.mode == 'BRAND'">
                    AND p.brand in
                    <foreach
                        collection="scope.allowedBrands"
                        item="allowedBrand"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{allowedBrand}
                    </foreach>
                </if>
            </where>
            group by p.product_id
            having SUM(oi.quantity) &gt; 0
        </script>
    """)
    List<Map<String,Object>> selectProductRanking(@Param("filter") Filter filter,
                                                  @Param("scope") Scope scope);

    @Select("""
        <script>
            select p.product_id as productId,
                   p.product_name as productName,
                   p.category as category,
                   p.brand as brand
            from product p
            where p.product_id in
            <foreach
                collection="productIds"
                item="productId"
                open="("
                separator=","
                close=")"
            >
                #{productId}
            </foreach>
        </script>
    """)
    List<Map<String,Object>> selectProductInfo(@Param("productIds") List<Long> productIds);

    @Select("""
        <script>
            select p.category as category,
                   ROUND(SUM(ROUND(oi.item_total, 2)), 2) as salesAmount,
                   SUM(oi.quantity) as salesVolume,
                   COUNT(DISTINCT o.order_id) as uniqueOrderCount
            from `order` o inner join order_item oi on o.order_id = oi.order_id
                inner join product p on p.product_id = oi.product_id
            <where>
                o.order_status = 'completed'
                AND o.order_date &gt;= #{filter.startTime}
                AND o.order_date &lt; DATE_ADD(#{filter.endTime}, INTERVAL 1 DAY)
                <if test="filter.categories != null and !filter.categories.isEmpty()">
                    AND p.category in
                    <foreach
                        collection="filter.categories"
                        item="category"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{category}
                    </foreach>
                </if>
                <if test="filter.brands != null and !filter.brands.isEmpty()">
                    AND p.brand in
                    <foreach
                        collection="filter.brands"
                        item="brand"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{brand}
                    </foreach>
                </if>
                <if test="scope.mode == 'BRAND'">
                    <choose>
                        <when test="scope.allowedBrands != null and !scope.allowedBrands.isEmpty()">
                            AND p.brand in
                            <foreach
                                collection="scope.allowedBrands"
                                item="allowedBrand"
                                open="("
                                separator=","
                                close=")"
                            >
                                #{allowedBrand}
                            </foreach>
                        </when>
                        <otherwise>
                            AND 1 = 0
                        </otherwise>
                    </choose>
                </if>
            </where>
            group by p.category
        </script>
    """)
    List<Map<String,Object>> selectProductCategoryStats(@Param("filter") Filter filter,
                                                        @Param("scope") Scope scope);

    @Select("""
        SELECT
            ROUND(SUM(ROUND(o.total_amount, 2)), 2) AS salesAmount,
            COUNT(*) AS uniqueOrderCount
        FROM `order` o
        WHERE o.order_status = 'completed'
          AND o.order_date >= #{startTime}
          AND o.order_date < DATE_ADD(#{endTime}, INTERVAL 1 DAY)
          AND NOT EXISTS (
              SELECT 1
              FROM order_item oi
              WHERE oi.order_id = o.order_id
          )
    """)
    Map<String,Object> selectUncategorizedStats(Filter filter);






}
