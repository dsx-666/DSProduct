package com.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.product.pojo.dto.request.OrderListRequest;
import com.product.pojo.po.Order;
import com.product.pojo.other.QueryOrderListResult;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OrderMapper extends BaseMapper<Order> {
    @Select("""
        <script>
            select o.order_id as orderId,
                   o.total_amount as amount,
                   o.order_status as status,
                   o.pay_way as payMethod,
                   u.name as userName,
                   o.order_date as createTime
            from `order` o left join `user` u on o.user_id = u.user_id
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
                <if test="keyWord != null and keyWord != ''">
                   AND EXISTS (
                         SELECT 1
                         FROM order_item oi
                         LEFT JOIN product p
                             ON oi.product_id = p.product_id
                         WHERE oi.order_id = o.order_id
                           AND p.product_name LIKE CONCAT('%', #{keyWord}, '%')
                     )
                </if>
            </where>
            ORDER BY
                o.order_date DESC,
                o.order_id ASC
            LIMIT #{page},#{num}
        </script>
    """)
    public List<QueryOrderListResult> selectOrderList(OrderListRequest orderInfoRequest);
    @Select("""
        <script>
            select count(*)
            from `order` o left join `user` u on o.user_id = u.user_id
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
                <if test="keyWord != null and keyWord != ''">
                   AND EXISTS (
                         SELECT 1
                         FROM order_item oi
                         LEFT JOIN product p
                             ON oi.product_id = p.product_id
                         WHERE oi.order_id = o.order_id
                           AND p.product_name LIKE CONCAT('%', #{keyWord}, '%')
                     )
                </if>
            </where>
        </script>
    """)
    public Long selectOrderListCount(OrderListRequest orderInfoRequest);
}
