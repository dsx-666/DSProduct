package com.product.controller;

import com.product.pojo.vo.Result;
import com.product.service.JwtBlacklistService;
import com.product.service.UserService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserLogoutControllerTest {
    @Test
    void shouldAddCurrentJwtToBlacklist() {
        UserService userService = mock(UserService.class);
        JwtBlacklistService jwtBlacklistService =
                mock(JwtBlacklistService.class);
        UserController controller = new UserController(
                userService,jwtBlacklistService
        );

        Result<Void> result = controller.Logout("Bearer jwt-token");

        assertEquals(200,result.getCode());
        assertNull(result.getData());
        verify(jwtBlacklistService).addBlacklist("jwt-token");
    }
}
