package com.product.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.po.Product;
import com.product.pojo.vo.Scope;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ProductMapper extends BaseMapper<Product> {
//    @Insert("${a}")
    default List<Object> selectCategories() {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Product::getCategory)
                .groupBy(Product::getCategory);
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
        select p.product_name as productName,
               sum(oi.quantity) as productCount
        from product p left join order_item oi on p.product_id = oi.product_id
        group by p.product_id
        <where>
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
        order by productCount desc
    </script>
    """)
    List<Object> selectAllProductCount(Scope scope);

}
