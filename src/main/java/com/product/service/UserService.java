package com.product.service;

import com.product.pojo.dto.request.UserRequest;
import com.product.pojo.dto.response.UserResponse;


public interface UserService {
    UserResponse registerUser(UserRequest createUserDto);
    UserResponse loginUser(UserRequest updateUserDto);

}
