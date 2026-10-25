package com.product.service.impl;

import com.product.MyException.BusinessException;
import com.product.config.BusinessProperties;
import com.product.mapper.UserMapper;
import com.product.pojo.bo.AdminUserListItem;
import com.product.pojo.dto.request.AdminUserListRequest;
import com.product.pojo.dto.response.AdminUserListResponse;
import com.product.pojo.other.JwtUtil;
import com.product.realtime.event.DataChangeEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserListServiceImplTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
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
                userMapper,passwordEncoder,authenticationManager,
                jwtUtil,businessProperties,filterValidator,
                dataChangeEventPublisher
        );
    }

    @Test
    void shouldNormalizeFiltersAndReturnPagedUsers() {
        AdminUserListRequest request = AdminUserListRequest.builder()
                .page(2L)
                .num(10L)
                .keyWord("  admin@example.test  ")
                .role("SUPER_ADMIN")
                .status("ACTIVE")
                .build();
        AdminUserListItem item = AdminUserListItem.builder()
                .userId(9001L)
                .email("admin@example.test")
                .name("林知远")
                .role("SUPER_ADMIN")
                .brand(null)
                .status("ACTIVE")
                .version(1L)
                .createdAt(LocalDate.of(2025,1,1))
                .build();
        when(userMapper.selectAdminUserList(request))
                .thenReturn(List.of(item));
        when(userMapper.selectAdminUserCount(request)).thenReturn(1L);

        AdminUserListResponse response = userService
                .getAdminUserList(request);

        assertEquals("admin@example.test",request.getKeyWord());
        assertEquals(10L,request.getOffset());
        assertEquals(1L,response.getTotal());
        assertEquals("超级管理员",
                response.getUserList().get(0).getRoleLabel());
        assertNull(response.getUserList().get(0).getBrand());
    }

    @Test
    void shouldTreatBlankKeywordAsMissingFilter() {
        AdminUserListRequest request = AdminUserListRequest.builder()
                .page(1L)
                .num(10L)
                .keyWord("   ")
                .build();
        when(userMapper.selectAdminUserList(request)).thenReturn(List.of());
        when(userMapper.selectAdminUserCount(request)).thenReturn(0L);

        AdminUserListResponse response = userService
                .getAdminUserList(request);

        assertNull(request.getKeyWord());
        assertEquals(0L,request.getOffset());
        assertEquals(List.of(),response.getUserList());
        assertEquals(0L,response.getTotal());
    }

    @Test
    void shouldRejectInvalidPageSizeRoleAndStatus() {
        assertParamError(AdminUserListRequest.builder()
                .page(0L).num(10L).build());
        assertParamError(AdminUserListRequest.builder()
                .page(1L).num(101L).build());
        assertParamError(AdminUserListRequest.builder()
                .page(1L).num(10L).role("ADMINISTRATOR").build());
        assertParamError(AdminUserListRequest.builder()
                .page(1L).num(10L).status("LOCKED").build());

        verify(userMapper,never()).selectAdminUserList(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldRejectKeywordLongerThanOneHundredCharacters() {
        AdminUserListRequest request = AdminUserListRequest.builder()
                .page(1L)
                .num(10L)
                .keyWord("a".repeat(101))
                .build();

        assertParamError(request);
    }

    private void assertParamError(AdminUserListRequest request) {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.getAdminUserList(request)
        );
        assertEquals(20001,exception.getCode());
    }
}
