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
