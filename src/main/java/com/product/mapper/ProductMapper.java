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






}
