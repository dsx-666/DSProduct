package com.product.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.product.converter.UserConverter;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.vo.Scope;
import com.product.pojo.po.User;
import com.product.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

// 实现SpringSecurity中UserDetailService接口
@RequiredArgsConstructor
@Component
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username)
    throws UsernameNotFoundException {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        //TODO: 注意由于是根据邮箱唯一性来进行登录所以业务上是username实则是Email
        wrapper.eq(User::getEmail, username);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new UsernameNotFoundException("用户名不存在");
        }
        return MyUserDetails.builder()
                .username(username)
                .password(user.getPassword())
                .scope(UserConverter.toScope(user))
                .user(user)
                .build();
    }
}
