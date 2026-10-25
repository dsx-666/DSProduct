package com.product.service.impl;

import com.product.MyException.ApiHttpException;
import com.product.config.BusinessProperties;
import com.product.enums.AccountStatus;
import com.product.enums.Role;
import com.product.mapper.UserMapper;
import com.product.pojo.bo.AdminUserListItem;
import com.product.pojo.dto.request.AdminUserCreateRequest;
import com.product.pojo.dto.request.AdminUserUpdateRequest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserWriteServiceImplTest {
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtUtil jwtUtil;
    @Mock BusinessProperties businessProperties;
    @Mock FilterValidator filterValidator;
    @Mock DataChangeEventPublisher dataChangeEventPublisher;

    private UserServiceImpl userService;
    private User operator;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userMapper,passwordEncoder,authenticationManager,
                jwtUtil,businessProperties,filterValidator,
                dataChangeEventPublisher
        );
        operator = User.builder()
                .userId(9001L)
                .role(Role.SUPER_ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        when(userMapper.selectById(9001L)).thenReturn(operator);
    }

    @Test
    void shouldCreateBrandAdmin() {
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setEmail(" operator@example.test ");
        request.setPassword("Demo123!");
        request.setName(" 演示管理员 ");
        request.setRole("ADMIN");
        request.setBrand(" NeoTech ");
        request.setStatus("ACTIVE");
        when(userMapper.selectEmailCount("operator@example.test"))
                .thenReturn(0L);
        when(userMapper.selectBrandCount("NeoTech")).thenReturn(1L);
        when(passwordEncoder.encode("Demo123!")).thenReturn("hash");
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(9005L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        AdminUserListItem result = userService.createAdminUser(request,operator);

        assertEquals(9005L,result.getUserId());
        assertEquals("operator@example.test",result.getEmail());
        assertEquals("演示管理员",result.getName());
        assertEquals("ADMIN",result.getRole());
        assertEquals("品牌管理员",result.getRoleLabel());
        assertEquals("NeoTech",result.getBrand());
        assertEquals("ACTIVE",result.getStatus());
        assertEquals(0L,result.getVersion());
        verify(dataChangeEventPublisher).publishUserChanged(
                "CREATED",9005L,9001L
        );
    }

    @Test
    void shouldRejectDuplicateEmailWithConflict() {
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setEmail("operator@example.test");
        request.setPassword("Demo123!");
        request.setName("演示管理员");
        request.setRole("ADMIN");
        request.setBrand("NeoTech");
        request.setStatus("ACTIVE");
        when(userMapper.selectEmailCount("operator@example.test"))
                .thenReturn(1L);

        ApiHttpException exception = assertThrows(
                ApiHttpException.class,
                () -> userService.createAdminUser(request,operator)
        );

        assertEquals(409,exception.getHttpStatus().value());
    }

    @Test
    void shouldUpdateWithOptimisticLockAndIncrementVersion() {
        User target = User.builder()
                .userId(9005L)
                .email("operator@example.test")
                .userName("原姓名")
                .role(Role.ADMIN)
                .brand("NeoTech")
                .accountStatus(AccountStatus.ACTIVE)
                .lockVersion(3L)
                .build();
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setVersion(3L);
        request.setName(" 新姓名 ");
        request.setBrand("Orion");
        when(userMapper.selectById(9005L)).thenReturn(target);
        when(userMapper.selectBrandCount("Orion")).thenReturn(1L);
        when(userMapper.updateAdminUserWithVersion(target,3L)).thenReturn(1);

        AdminUserListItem result = userService.updateAdminUser(
                9005L,request,operator
        );

        assertEquals("新姓名",result.getName());
        assertEquals("Orion",result.getBrand());
        assertEquals(4L,result.getVersion());
        verify(userMapper).updateAdminUserWithVersion(target,3L);
        verify(dataChangeEventPublisher).publishUserChanged(
                "UPDATED",9005L,9001L
        );
    }

    @Test
    void shouldRequireExplicitBrandClearWhenChangingToNormalUser() {
        User target = User.builder()
                .userId(9005L)
                .role(Role.ADMIN)
                .brand("NeoTech")
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setVersion(1L);
        request.setRole("NORMAL_USER");
        when(userMapper.selectById(9005L)).thenReturn(target);

        ApiHttpException exception = assertThrows(
                ApiHttpException.class,
                () -> userService.updateAdminUser(
                        9005L,request,
                        operator)
        );

        assertEquals(400,exception.getHttpStatus().value());
    }
}
