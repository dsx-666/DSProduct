package com.product.service;

import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.request.AdminUserListRequest;
import com.product.pojo.dto.request.AdminUserCreateRequest;
import com.product.pojo.dto.request.AdminUserUpdateRequest;
import com.product.pojo.dto.request.UserRequest;
import com.product.pojo.dto.response.UserCustomerStatsResponse;
import com.product.pojo.dto.response.UserResponse;
import com.product.pojo.dto.response.AdminUserListResponse;
import com.product.pojo.po.User;
import com.product.pojo.bo.AdminUserListItem;


public interface UserService {
    UserResponse registerUser(UserRequest createUserDto);
    UserResponse loginUser(UserRequest updateUserDto);
    UserCustomerStatsResponse getUserCustomerStats(Filter filter,User user);
    AdminUserListResponse getAdminUserList(AdminUserListRequest request);
    AdminUserListItem createAdminUser(
            AdminUserCreateRequest request,
            User operator);
    AdminUserListItem updateAdminUser(
            Long userId,
            AdminUserUpdateRequest request,
            User operator);

}
