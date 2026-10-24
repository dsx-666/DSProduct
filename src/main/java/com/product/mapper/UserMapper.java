package com.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.product.pojo.bo.Scope;
import com.product.pojo.bo.AdminUserListItem;
import com.product.pojo.dto.request.AdminUserListRequest;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.po.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;
//import org.apache.ibatis.annotations.*;
//@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("""
        <script>
        select case when u.user_id is null or u.gender is null or trim(u.gender)='' then 'UNKNOWN'
                    when u.gender in ('Male','Female','Other') then u.gender else 'UNKNOWN' end as gender,
               count(distinct o.user_id) as buyerCount,
               count(distinct o.order_id) as orderCount,
               ROUND(SUM(distinct_order.salesAmount),2) as salesAmount
        from `order` o left join `user` u on u.user_id=o.user_id
        left join (
          select o2.order_id,case when count(oi.order_item_id)=0 then round(o2.total_amount,2)
            else round(sum(round(oi.item_total,2)),2) end as salesAmount
          from `order` o2 left join order_item oi on oi.order_id=o2.order_id
            left join product p on p.product_id=oi.product_id
          where o2.order_status='completed' and o2.order_date &gt;= #{filter.startTime}
            and o2.order_date &lt; DATE_ADD(#{filter.endTime},INTERVAL 1 DAY)
            <if test="categories != null and !categories.isEmpty()">AND p.category in <foreach collection="categories" item="v" open="(" separator="," close=")">#{v}</foreach></if>
            <if test="brands != null and !brands.isEmpty()">AND p.brand in <foreach collection="brands" item="v" open="(" separator="," close=")">#{v}</foreach></if>
            <if test="allowedBrands != null"><choose><when test="allowedBrands.isEmpty()">AND 1=0</when><otherwise>AND p.brand in <foreach collection="allowedBrands" item="v" open="(" separator="," close=")">#{v}</foreach></otherwise></choose></if>
          group by o2.order_id
        ) distinct_order on distinct_order.order_id=o.order_id
        where o.order_status='completed' and o.order_date &gt;= #{filter.startTime}
          and o.order_date &lt; DATE_ADD(#{filter.endTime},INTERVAL 1 DAY)
          <if test="categories != null and !categories.isEmpty() or brands != null and !brands.isEmpty() or allowedBrands != null">
            and exists(select 1 from order_item oi join product p on p.product_id=oi.product_id where oi.order_id=o.order_id
              <if test="categories != null and !categories.isEmpty()">AND p.category in <foreach collection="categories" item="v" open="(" separator="," close=")">#{v}</foreach></if>
              <if test="brands != null and !brands.isEmpty()">AND p.brand in <foreach collection="brands" item="v" open="(" separator="," close=")">#{v}</foreach></if>
              <if test="allowedBrands != null"><choose><when test="allowedBrands.isEmpty()">AND 1=0</when><otherwise>AND p.brand in <foreach collection="allowedBrands" item="v" open="(" separator="," close=")">#{v}</foreach></otherwise></choose></if>)
          </if>
        group by gender order by gender
        </script>
    """)
    List<Map<String,Object>> selectBuyerGenderStats(@Param("filter") Filter filter,
            @Param("categories") List<String> categories,@Param("brands") List<String> brands,
            @Param("allowedBrands") List<String> allowedBrands);
    @Select("select count(*) from `user` where email = #{email}")
    Long selectEmailCount(@Param("email") String email);

    @Select("select count(*) from product where brand = #{brand}")
    Long selectBrandCount(@Param("brand") String brand);

    @Update("""
        update `user`
        set name = #{user.userName},
            role = #{user.role},
            brand = #{user.brand},
            account_status = #{user.accountStatus},
            lock_version = lock_version + 1
        where user_id = #{user.userId}
          and lock_version = #{expectedVersion}
    """)
    int updateAdminUserWithVersion(
            @Param("user") User user,
            @Param("expectedVersion") Long expectedVersion);

    @Select("""
        <script>
            select u.user_id as userId,
                   u.email as email,
                   u.name as name,
                   u.role as role,
                   u.brand as brand,
                   u.account_status as status,
                   u.lock_version as version,
                   u.signup_date as createdAt
            from `user` u
            <where>
                <if test="request.keyWord != null">
                    AND (
                        LOCATE(#{request.keyWord},u.name) &gt; 0
                        OR LOCATE(#{request.keyWord},u.email) &gt; 0
                        OR CAST(u.user_id AS CHAR) = #{request.keyWord}
                    )
                </if>
                <if test="request.role != null">
                    AND u.role = #{request.role}
                </if>
                <if test="request.status != null">
                    AND u.account_status = #{request.status}
                </if>
            </where>
            order by case when u.role = 'SUPER_ADMIN' then 0 else 1 end,
                     u.user_id desc
            limit #{request.offset},#{request.num}
        </script>
    """)
    List<AdminUserListItem> selectAdminUserList(
            @Param("request") AdminUserListRequest request);

    @Select("""
        <script>
            select count(*)
            from `user` u
            <where>
                <if test="request.keyWord != null">
                    AND (
                        LOCATE(#{request.keyWord},u.name) &gt; 0
                        OR LOCATE(#{request.keyWord},u.email) &gt; 0
                        OR CAST(u.user_id AS CHAR) = #{request.keyWord}
                    )
                </if>
                <if test="request.role != null">
                    AND u.role = #{request.role}
                </if>
                <if test="request.status != null">
                    AND u.account_status = #{request.status}
                </if>
            </where>
        </script>
    """)
    Long selectAdminUserCount(
            @Param("request") AdminUserListRequest request);

    @Select("""
        <script>
            select distinct o.user_id as userId,
                   u.signup_date as signupDate
            from `order` o left join `user` u on o.user_id = u.user_id
                left join order_item oi on o.order_id = oi.order_id
                left join product p on p.product_id = oi.product_id
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
            order by o.user_id asc
        </script>
    """)
    List<Map<String,Object>> selectPurchaseUserList(@Param("filter") Filter filter,
                                                    @Param("scope") Scope scope);
}
