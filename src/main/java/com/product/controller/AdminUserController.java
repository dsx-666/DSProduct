package com.product.controller;

import com.product.MyException.BusinessException;
import com.product.enums.Code;
import com.product.pojo.dto.request.AdminUserListRequest;
import com.product.pojo.dto.request.AdminUserCreateRequest;
import com.product.pojo.dto.request.AdminUserUpdateRequest;
import com.product.pojo.dto.response.AdminUserListResponse;
import com.product.pojo.bo.AdminUserListItem;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.vo.AdminUserListVo;
import com.product.pojo.vo.Result;
import com.product.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {
    private final UserService userService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/users")
    public Result<AdminUserListVo> getAdminUserList(
            @ModelAttribute AdminUserListRequest request,
            HttpServletRequest httpServletRequest) {
        Set<String> allowedParams = Set.of(
                "page","num","keyWord","role","status"
        );
        if(!allowedParams.containsAll(
                httpServletRequest.getParameterMap().keySet())){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":存在不支持的参数");
        }

        AdminUserListResponse response = userService
                .getAdminUserList(request);

        return Result.success(
                AdminUserListVo.builder()
                        .total(response.getTotal())
                        .pageNum(request.getPage())
                        .pageSize(request.getNum())
                        .userList(response.getUserList())
                        .build()
        );
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/users")
    public Result<AdminUserListItem> createAdminUser(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @RequestBody(required = false) AdminUserCreateRequest request) {
        return Result.success(userService.createAdminUser(
                request,myUserDetails.getUser()
        ));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PatchMapping("/users/{userId}")
    public Result<AdminUserListItem> updateAdminUser(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @PathVariable Long userId,
            @RequestBody(required = false) AdminUserUpdateRequest request) {
        return Result.success(userService.updateAdminUser(
                userId,request,myUserDetails.getUser()
        ));
    }
}
