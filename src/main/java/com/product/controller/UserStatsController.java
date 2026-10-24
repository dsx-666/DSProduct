package com.product.controller;

import com.product.MyException.BusinessException;
import com.product.enums.Code;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.response.UserCustomerStatsResponse;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.vo.Result;
import com.product.pojo.vo.UserCustomerStatsVo;
import com.product.pojo.dto.response.GenderStatsResponse;
import com.product.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/user")
public class UserStatsController {
    private final UserService userService;
    // TODO: 可以尝试加一个redis存放用户注册时间
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/customer-stats")
    public Result<UserCustomerStatsVo> getUserCustomerStats(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @ModelAttribute Filter filter) {

        UserCustomerStatsResponse userCustomerStatsResponse = userService
                .getUserCustomerStats(filter,myUserDetails.getUser());

        return Result.success(
                UserCustomerStatsVo.builder()
                        .filters(filter)
                        .scope(userCustomerStatsResponse.getScope())
                        .businessDate(userCustomerStatsResponse.getBusinessDate())
                        .newUserDays(userCustomerStatsResponse.getNewUserDays())
                        .totalPurchaseUserCount(userCustomerStatsResponse
                                .getTotalPurchaseUserCount())
                        .newUserCount(userCustomerStatsResponse.getNewUserCount())
                        .oldUserCount(userCustomerStatsResponse.getOldUserCount())
                        .validPurchaseUserCount(userCustomerStatsResponse
                                .getValidPurchaseUserCount())
                        .newUserRatio(userCustomerStatsResponse.getNewUserRatio())
                        .oldUserRatio(userCustomerStatsResponse.getOldUserRatio())
                        .abnormalUserCount(userCustomerStatsResponse
                                .getAbnormalUserCount())
                        .abnormalUserRatio(userCustomerStatsResponse
                                .getAbnormalUserRatio())
                        .build()
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @GetMapping("/gender-stats")
    public Result<GenderStatsResponse> getGenderStats(
            @AuthenticationPrincipal MyUserDetails principal,
            @ModelAttribute Filter filter,
            HttpServletRequest request) {
        if(!Set.of("startTime","endTime","category","brand").containsAll(request.getParameterMap().keySet()))
            throw new BusinessException(Code.ParamError.getCode(),"参数错误");
        return Result.success(userService.getGenderStats(filter,principal.getUser()));
    }
}
