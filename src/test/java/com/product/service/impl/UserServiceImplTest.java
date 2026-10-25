package com.product.service.impl;

import com.product.MyException.BusinessException;
import com.product.config.BusinessProperties;
import com.product.converter.UserConverter;
import com.product.enums.Role;
import com.product.mapper.UserMapper;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.response.UserCustomerStatsResponse;
import com.product.pojo.other.JwtUtil;
import com.product.pojo.po.User;
import com.product.realtime.event.DataChangeEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager passwordAuthenticationManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private BusinessProperties businessProperties;
    @Mock
    private FilterValidator filterValidator;
    @Mock
    private DataChangeEventPublisher dataChangeEventPublisher;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userMapper,
                passwordEncoder,
                passwordAuthenticationManager,
                jwtUtil,
                businessProperties,
                filterValidator,
                dataChangeEventPublisher
        );
    }

    @Test
    void shouldClassifyPurchaseUsersByFixedBusinessDate() {
        Filter filter = createFilter(List.of(),List.of());
        User user = User.builder().role(Role.SUPER_ADMIN).build();
        when(businessProperties.getTime())
                .thenReturn(LocalDate.of(2025,11,14));
        when(userMapper.selectPurchaseUserList(
                filter,UserConverter.toScope(user)))
                .thenReturn(List.of(
                        purchaseUser(1L,LocalDate.of(2025,10,16)),
                        purchaseUser(2L,Date.valueOf("2025-10-15")),
                        purchaseUser(3L,LocalDate.of(2025,11,15)),
                        purchaseUser(4L,null),
                        purchaseUser(5L,"invalid-date")
                ));

        UserCustomerStatsResponse response = userService
                .getUserCustomerStats(filter,user);

        assertEquals(LocalDate.of(2025,11,14),response.getBusinessDate());
        assertEquals(30,response.getNewUserDays());
        assertEquals(5,response.getTotalPurchaseUserCount());
        assertEquals(1,response.getNewUserCount());
        assertEquals(1,response.getOldUserCount());
        assertEquals(2,response.getValidPurchaseUserCount());
        assertEquals(new BigDecimal("0.500000"),response.getNewUserRatio());
        assertEquals(new BigDecimal("0.500000"),response.getOldUserRatio());
        assertEquals(3,response.getAbnormalUserCount());
        assertEquals(new BigDecimal("0.600000"),response.getAbnormalUserRatio());
        assertEquals(List.of("completed"),filter.getStatuses());
    }

    @Test
    void shouldReturnZeroCountsAndRatiosWhenThereAreNoPurchaseUsers() {
        Filter filter = createFilter(List.of(),List.of());
        User user = User.builder().role(Role.SUPER_ADMIN).build();
        when(businessProperties.getTime())
                .thenReturn(LocalDate.of(2025,11,14));
        when(userMapper.selectPurchaseUserList(
                filter,UserConverter.toScope(user)))
                .thenReturn(List.of());

        UserCustomerStatsResponse response = userService
                .getUserCustomerStats(filter,user);

        assertEquals(0,response.getTotalPurchaseUserCount());
        assertEquals(0,response.getValidPurchaseUserCount());
        assertEquals(BigDecimal.ZERO,response.getNewUserRatio());
        assertEquals(BigDecimal.ZERO,response.getOldUserRatio());
        assertEquals(BigDecimal.ZERO,response.getAbnormalUserRatio());
    }

    @Test
    void shouldUseManagedBrandScopeForAdmin() {
        Filter filter = createFilter(List.of(),List.of());
        User user = User.builder()
                .role(Role.ADMIN)
                .brand("NeoTech")
                .build();
        when(businessProperties.getTime())
                .thenReturn(LocalDate.of(2025,11,14));
        when(userMapper.selectPurchaseUserList(
                filter,UserConverter.toScope(user)))
                .thenReturn(List.of(
                        purchaseUser(1L,LocalDate.of(2025,11,1)),
                        purchaseUser(2L,LocalDate.of(2024,1,1))
                ));

        UserCustomerStatsResponse response = userService
                .getUserCustomerStats(filter,user);

        assertEquals("BRAND",response.getScope().getMode());
        assertEquals(List.of("NeoTech"),response.getScope().getAllowedBrands());
        assertEquals(2,response.getTotalPurchaseUserCount());
        assertEquals(1,response.getNewUserCount());
        assertEquals(1,response.getOldUserCount());
        assertEquals(0,response.getAbnormalUserCount());
    }

    @Test
    void shouldRejectBrandOutsideAdminScope() {
        Filter filter = createFilter(List.of(),List.of("Orion"));
        User user = User.builder()
                .role(Role.ADMIN)
                .brand("NeoTech")
                .build();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.getUserCustomerStats(filter,user)
        );

        assertEquals(403,exception.getCode());
        verify(userMapper,never()).selectPurchaseUserList(
                filter,UserConverter.toScope(user)
        );
    }

    private Filter createFilter(List<String> categories,List<String> brands) {
        return Filter.builder()
                .startTime(LocalDate.of(2025,11,12))
                .endTime(LocalDate.of(2025,11,14))
                .categories(categories)
                .brands(brands)
                .statuses(List.of())
                .build();
    }

    private Map<String,Object> purchaseUser(long userId,Object signupDate) {
        Map<String,Object> result = new HashMap<>();
        result.put("userId",userId);
        result.put("signupDate",signupDate);
        return result;
    }
}
