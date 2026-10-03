package com.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.po.Order;
import com.product.pojo.bo.QueryOrderListResult;
import com.product.pojo.vo.Scope;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

public interface OrderMapper extends BaseMapper<Order> {
    @Select("""
        <script>
            select o.order_id as orderId,
                   o.total_amount as amount,
                   o.order_status as status,
                   o.pay_way as payWay,
                   u.name as userName,
                   o.order_date as createTime,
                   count(*) as productCount,
                   sum(oi.item_total) as matchedAmount,
                   GROUP_CONCAT(
                                   DISTINCT product_name
                                   ORDER BY product_name ASC
                                   SEPARATOR '、'
                               ) AS productSummary,
                   u.user_id as userId
            from `order` o left join `user` u on o.user_id = u.user_id
                left join order_item oi on o.order_id = oi.order_id
                left join product p on p.product_id = oi.product_id
            <where>
                o.order_date &gt;= #{filters.startTime}
                AND o.order_date &lt; DATE_ADD(#{filters.endTime}, INTERVAL 1 DAY)
                <if test="filters.statuses != null and !filters.statuses.isEmpty()">
                    AND o.order_status in
                    <foreach
                        collection="filters.statuses"
                        item="status"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{status}
                    </foreach>
                </if>
                <if test="keyWord != null and keyWord != ''">
                   AND EXISTS (
                         SELECT 1
                         FROM order_item oi_
                         LEFT JOIN product p
                             ON oi.product_id = p.product_id
                         WHERE oi_.order_item_id = oi.order_item_id
                           AND p.product_name LIKE CONCAT('%', #{keyWord}, '%')
                     )
                </if>
                <if test="filters.categories != null and !filters.categories.isEmpty()">
                    AND p.category in
                    <foreach
                        collection="filters.categories"
                        item="category"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{category}
                    </foreach>
                </if>
                <if test="filters.brands != null and !filters.brands.isEmpty()">
                    AND p.brand in
                    <foreach
                        collection="filters.brands"
                        item="brand"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{brand}
                    </foreach>
                </if>
            </where>
            group by o.order_id
            ORDER BY
                o.order_date DESC,
                o.order_id ASC
            LIMIT #{offset},#{num}
        </script>
    """)
    List<QueryOrderListResult> selectOrderList(OrderListRequest orderInfoRequest);
    @Select("""
        <script>
            select count(*)
            from `order` o left join `user` u on o.user_id = u.user_id
                left join order_item oi on o.order_id = oi.order_id
                left join product p on p.product_id = oi.product_id
            <where>
                o.order_date &gt;= #{filters.startTime}
                AND o.order_date &lt; DATE_ADD(#{filters.endTime}, INTERVAL 1 DAY)
                <if test="filters.statuses != null and !filters.statuses.isEmpty()">
                    AND o.order_status in
                    <foreach
                        collection="filters.statuses"
                        item="status"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{status}
                    </foreach>
                </if>
                <if test="keyWord != null and keyWord != ''">
                   AND EXISTS (
                         SELECT 1
                         FROM order_item oi_
                         LEFT JOIN product p
                             ON oi.product_id = p.product_id
                         WHERE oi_.order_item_id = oi.order_item_id
                           AND p.product_name LIKE CONCAT('%', #{keyWord}, '%')
                     )
                </if>
                <if test="filters.categories != null and !filters.categories.isEmpty()">
                    AND p.category in
                    <foreach
                        collection="filters.categories"
                        item="category"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{category}
                    </foreach>
                </if>
                <if test="filters.brands != null and !filters.brands.isEmpty()">
                    AND p.brand in
                    <foreach
                        collection="filters.brands"
                        item="brand"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{brand}
                    </foreach>
                </if>
            </where>
        </script>
    """)
    Long selectOrderListCount(OrderListRequest orderInfoRequest);

    @Select("""
            <script>
            select sum(oi.item_total) as originalAmount
            from `order` o left join order_item oi on o.order_id = oi.order_id
                    left join product p on p.product_id = oi.product_id
            <where>
                <if test="orderIdList != null and !orderIdList.isEmpty()">
                    AND o.order_id in
                    <foreach
                        collection="orderIdList"
                        item="orderId"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{orderId}
                    </foreach>
                </if>
            </where>
            group by o.order_id
            ORDER BY
                o.order_date DESC,
                o.order_id ASC
            </script>
            """)
    List<BigDecimal> selectOrderOriginalAmount(@Param("orderIdList") List<Long> orderIdList);

    @Select("""
           <script>
           select sum(oi.item_total) as visibleAmount
           from `order` o left join `user` u on o.user_id = u.user_id
                left join order_item oi on o.order_id = oi.order_id
                left join product p on p.product_id = oi.product_id
           <where>
                <if test="orderIdList != null and !orderIdList.isEmpty()">
                    AND o.order_id in
                    <foreach
                        collection="orderIdList"
                        item="orderId"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{orderId}
                    </foreach>
                </if>
                <if test="scope.allowedBrands != null and !scope.allowedBrands.isEmpty()">
                    AND p.brand in
                    <foreach
                        collection="scope.allowedBrands"
                        item="brand"
                        open="("
                        separator=","
                        close=")"
                    >
                        #{brand}
                    </foreach>
                </if>
           </where>
           group by o.order_id
           ORDER BY
                o.order_date DESC,
                o.order_id ASC
           </script>
           """)
    List<BigDecimal> selectVisibleAmountByBrand(@Param("scope") Scope scope,@Param("orderIdList") List<Long> orderIdList);


}
