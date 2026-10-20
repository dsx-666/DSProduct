package com.product.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.product.MyException.BusinessException;
import com.product.MyException.ApiHttpException;
import com.product.config.BusinessProperties;
import com.product.converter.UserConverter;
import com.product.pojo.bo.Scope;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.request.AdminUserListRequest;
import com.product.pojo.dto.request.AdminUserCreateRequest;
import com.product.pojo.dto.request.AdminUserUpdateRequest;
import com.product.pojo.dto.request.UserRequest;
import com.product.pojo.dto.response.UserCustomerStatsResponse;
import com.product.pojo.dto.response.UserResponse;
import com.product.pojo.dto.response.AdminUserListResponse;
import com.product.pojo.bo.AdminUserListItem;
import com.product.pojo.other.JwtUtil;
import com.product.pojo.other.MyUserDetails;
import com.product.enums.Role;
import com.product.enums.AccountStatus;
import com.product.pojo.po.User;
import com.product.enums.Code;
import com.product.mapper.UserMapper;
import com.product.enums.UserType;
import com.product.security.token.PasswordAuthenticationToken;
import com.product.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

//仅给final 字段 + @NonNull 字段加入构造方法，可以用于构造器注入
@Slf4j
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager passwordAuthenticationManager;
    private final JwtUtil jwtUtil;
    private final BusinessProperties businessProperties;
    private final FilterValidator filterValidator;
    @Override
    // 事务同成功同失败
    @Transactional(rollbackFor = Exception.class)
    public UserResponse registerUser(UserRequest registerUserDto) {
        // 对验证密码进行校验
        if(!registerUserDto.getPassword().
                equals(registerUserDto.getConfirmPassword())) {
            throw new BusinessException(Code.SameError.getCode(),
                    "验证密码"+Code.SameError.getDesc());
        }
        // 对邮箱唯一性进行判断
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, registerUserDto.getEmail());
        if(userMapper.exists(wrapper)) {
            throw new BusinessException(Code.UniqueError.getCode(),
                    "邮箱"+ Code.UniqueError.getDesc());
        }
        // 创建user 实体类
        User user = User.builder()
                .userName(registerUserDto.getUserName())
                .password(passwordEncoder
                        .encode(registerUserDto.getPassword()))
                .gender(registerUserDto.getGender())
                .email(registerUserDto.getEmail())
                .city(registerUserDto.getCity())
                .role(Role.NORMAL_USER)
                .userType(UserType.NEW_USER)
                .signupDate(businessProperties.getTime())
                .brand(null)
                .build();
        // 插入数据库
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(Code.UniqueError.getCode(),
                    "邮箱" + Code.UniqueError.getDesc());
        }
        // 返回给前端数据

        return UserResponse.builder()
                .userName(user.getUserName())
                .userId(user.getUserId())
                .createTime(user.getSignupDate())
                .role(user.getRole())
                .brand(user.getBrand())
                .email(user.getEmail())
                .jwtToken(jwtUtil.generateToken(
                        user.getUserId(),
                        user.getEmail()
                ))
                .scope(UserConverter.toScope(user))
                .build();

    }
    // 利用AuthenticationManager来进行验证
    @Override
    public UserResponse loginUser(UserRequest loginUserDto) {

        PasswordAuthenticationToken token =
                new PasswordAuthenticationToken(
                        loginUserDto.getEmail(),
                        loginUserDto.getPassword()
                );
        // 捕获异常
        Authentication authentication;
        try {
            // 注意：try 的作用域不是整个函数，而是 try {} 这一对大括号形成的代码块。
            authentication = passwordAuthenticationManager.authenticate(token);

        } catch (UsernameNotFoundException e) {

            throw new BusinessException(Code.RegisterError.getCode(),
                    Code.RegisterError.getDesc()
            );

        } catch (BadCredentialsException e) {

            throw new BusinessException(Code.LoginError.getCode(),
                    Code.LoginError.getDesc()
            );

        } catch (Exception e) {

            throw new RuntimeException(e);

        }
        MyUserDetails userDetails = (MyUserDetails) authentication.getPrincipal();
        return UserResponse.builder()
                .userName(userDetails.getUser().getUserName())
                .jwtToken(jwtUtil.generateToken(userDetails.getUser().getUserId(),
                        userDetails.getUsername()))
                .email(userDetails.getUser().getEmail())
                .userId(userDetails.getUser().getUserId())
                .role(userDetails.getUser().getRole())
                .brand(userDetails.getUser().getBrand())
                .scope(userDetails.getScope())
                .createTime(userDetails.getUser().getSignupDate())
                .build();

    }

    @Override
    public UserCustomerStatsResponse getUserCustomerStats(Filter filter,User user) {
        filter.setStatuses(List.of("completed"));
        filterValidator.validate(filter);

        Scope scope = UserConverter.toScope(user);
        if(!scope.validateFilter(filter)){
            throw new BusinessException(Code.AccessDeniedError.getCode(),
                    Code.AccessDeniedError.getDesc());
        }

        List<Map<String,Object>> purchaseUserList = userMapper
                .selectPurchaseUserList(filter,scope);
        long newUserCount = 0;
        long oldUserCount = 0;
        long abnormalUserCount = 0;
        LocalDate businessDate = businessProperties.getTime();

        for(Map<String,Object> item : purchaseUserList){
            LocalDate signupDate = toLocalDate(item.get("signupDate"));
            if(signupDate == null || signupDate.isAfter(businessDate)){
                abnormalUserCount++;
                continue;
            }

            long signupDays = ChronoUnit.DAYS.between(signupDate,businessDate);
            if(signupDays < 30){
                newUserCount++;
            }else{
                oldUserCount++;
            }
        }

        long validPurchaseUserCount = newUserCount + oldUserCount;
        long totalPurchaseUserCount = validPurchaseUserCount + abnormalUserCount;

        return UserCustomerStatsResponse.builder()
                .scope(scope)
                .businessDate(businessDate)
                .newUserDays((long) 30)
                .totalPurchaseUserCount(totalPurchaseUserCount)
                .newUserCount(newUserCount)
                .oldUserCount(oldUserCount)
                .validPurchaseUserCount(validPurchaseUserCount)
                .newUserRatio(getRatio(newUserCount,validPurchaseUserCount))
                .oldUserRatio(getRatio(oldUserCount,validPurchaseUserCount))
                .abnormalUserCount(abnormalUserCount)
                .abnormalUserRatio(getRatio(
                        abnormalUserCount,totalPurchaseUserCount))
                .build();
    }

    @Override
    public AdminUserListResponse getAdminUserList(
            AdminUserListRequest request) {
        validateAdminUserListRequest(request);

        List<AdminUserListItem> userList = userMapper
                .selectAdminUserList(request);
        userList.forEach(item -> item.setRoleLabel(
                Role.fromRole(item.getRole()).getRoleLabel()
        ));

        return AdminUserListResponse.builder()
                .total(userMapper.selectAdminUserCount(request))
                .userList(userList)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserListItem createAdminUser(
            AdminUserCreateRequest request,
            User operator) {
        validateCurrentSuperAdmin(operator);
        validateCreateAdminUserRequest(request);

        String email = request.getEmail().trim();
        if(userMapper.selectEmailCount(email) > 0){
            throw new ApiHttpException(HttpStatus.CONFLICT,409,"邮箱已被占用");
        }

        String brand = normalizeAndValidateBrand(request.getBrand());
        AccountStatus status = parseAccountStatus(request.getStatus());
        if(status == AccountStatus.ACTIVE && brand == null){
            throw badRequest("启用的品牌管理员必须分配品牌");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .userName(normalizeName(request.getName()))
                .role(Role.ADMIN)
                .brand(brand)
                .accountStatus(status)
                .lockVersion(0L)
                .signupDate(LocalDate.now(BUSINESS_ZONE))
                .userType(null)
                // 兼容当前基础表中的非空历史资料列；管理接口不公开这些值。
                .gender("")
                .city("")
                .build();
        try{
            userMapper.insert(user);
        }catch (DuplicateKeyException e){
            throw new ApiHttpException(HttpStatus.CONFLICT,409,"邮箱已被占用");
        }
        return toAdminUserListItem(user);
    }
    // 使用了乐观锁
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserListItem updateAdminUser(
            Long userId,
            AdminUserUpdateRequest request,
            User operator) {
        validateCurrentSuperAdmin(operator);
        if(userId == null || userId <= 0){
            throw badRequest("userId必须为正整数");
        }
        validateUpdateAdminUserRequest(request);

        User target = userMapper.selectById(userId);
        if(target == null){
            throw new ApiHttpException(HttpStatus.NOT_FOUND,404,"人员不存在");
        }
        if(operator != null && userId.equals(operator.getUserId())){
            throw new ApiHttpException(HttpStatus.FORBIDDEN,403,"不能编辑本人账号");
        }
        if(target.getRole() == Role.SUPER_ADMIN){
            throw new ApiHttpException(HttpStatus.FORBIDDEN,403,"不能编辑超级管理员");
        }

        Role finalRole = request.isRoleProvided()
                ? parseEditableRole(request.getRole())
                : target.getRole();
        AccountStatus finalStatus = request.isStatusProvided()
                ? parseAccountStatus(request.getStatus())
                : defaultAccountStatus(target.getAccountStatus());
        String finalName = request.isNameProvided()
                ? normalizeName(request.getName())
                : target.getUserName();
        String finalBrand = request.isBrandProvided()
                ? normalizeAndValidateBrand(request.getBrand())
                : target.getBrand();

        if(target.getRole() == Role.ADMIN
                && finalRole == Role.NORMAL_USER
                && (!request.isBrandProvided() || request.getBrand() != null)){
            throw badRequest("管理员改为普通用户时必须显式将brand设为null");
        }
        if(finalRole == Role.NORMAL_USER && finalBrand != null){
            throw badRequest("普通用户不能保留品牌归属");
        }
        if(finalRole == Role.ADMIN
                && finalStatus == AccountStatus.ACTIVE
                && finalBrand == null){
            throw badRequest("启用的品牌管理员必须分配品牌");
        }
        if(target.getRole() == Role.NORMAL_USER
                && finalRole == Role.ADMIN
                && finalStatus == AccountStatus.ACTIVE
                && !request.isBrandProvided()){
            throw badRequest("普通用户提升为启用管理员时必须指定品牌");
        }

        target.setUserName(finalName);
        target.setRole(finalRole);
        target.setBrand(finalBrand);
        target.setAccountStatus(finalStatus);
        int affected = userMapper.updateAdminUserWithVersion(
                target,request.getVersion()
        );
        if(affected == 0){
            if(userMapper.selectById(userId) == null){
                throw new ApiHttpException(HttpStatus.NOT_FOUND,404,"人员不存在");
            }
            throw new ApiHttpException(HttpStatus.CONFLICT,409,
                    "人员信息已被修改，请重新查询后再提交");
        }
        target.setLockVersion(request.getVersion()+1);
        return toAdminUserListItem(target);
    }

    private void validateCreateAdminUserRequest(AdminUserCreateRequest request) {
        if(request == null){
            throw badRequest("请求体不能为空");
        }
        if(!request.getUnsupportedFields().isEmpty()){
            throw badRequest("包含不支持的字段");
        }
        String email = request.getEmail();
        if(email == null || email.isBlank() || email.length() > 255
                || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")){
            throw badRequest("email格式不合法");
        }
        if(request.getPassword() == null
                || request.getPassword().length() < 8
                || request.getPassword().length() > 64){
            throw badRequest("password长度必须为8到64个字符");
        }
        normalizeName(request.getName());
        if(!"ADMIN".equals(request.getRole())){
            throw badRequest("role只能为ADMIN");
        }
        if(!request.isBrandProvided()){
            throw badRequest("brand字段必须出现");
        }
        parseAccountStatus(request.getStatus());
    }

    private void validateCurrentSuperAdmin(User operator) {
        if(operator == null || operator.getUserId() == null){
            throw new ApiHttpException(HttpStatus.UNAUTHORIZED,401,"身份验证失败");
        }
        User currentOperator = userMapper.selectById(operator.getUserId());
        if(currentOperator == null
                || currentOperator.getAccountStatus() == AccountStatus.DISABLED){
            throw new ApiHttpException(HttpStatus.UNAUTHORIZED,401,"身份验证失败");
        }
        if(currentOperator.getRole() != Role.SUPER_ADMIN){
            throw new ApiHttpException(HttpStatus.FORBIDDEN,403,"无权访问");
        }
    }

    private void validateUpdateAdminUserRequest(AdminUserUpdateRequest request) {
        if(request == null){
            throw badRequest("请求体不能为空");
        }
        if(!request.getUnsupportedFields().isEmpty()){
            throw badRequest("包含不支持的字段");
        }
        if(request.getVersion() == null || request.getVersion() < 0){
            throw badRequest("version必须为非负整数");
        }
        if(!request.hasMutableField()){
            throw badRequest("除version外至少提供一个可修改字段");
        }
        if(request.isNameProvided()){
            normalizeName(request.getName());
        }
        if(request.isRoleProvided()){
            parseEditableRole(request.getRole());
        }
        if(request.isStatusProvided()){
            parseAccountStatus(request.getStatus());
        }
    }

    private String normalizeName(String name) {
        if(name == null){
            throw badRequest("name不能为空");
        }
        String normalized = name.trim();
        if(normalized.isEmpty() || normalized.length() > 40){
            throw badRequest("name长度必须为1到40个字符");
        }
        return normalized;
    }

    private String normalizeAndValidateBrand(String brand) {
        if(brand == null){
            return null;
        }
        String normalized = brand.trim();
        if(normalized.isEmpty()){
            throw badRequest("brand不能是空字符串");
        }
        if(userMapper.selectBrandCount(normalized) == 0){
            throw badRequest("brand不是有效品牌");
        }
        return normalized;
    }

    private Role parseEditableRole(String role) {
        if("NORMAL_USER".equals(role)){
            return Role.NORMAL_USER;
        }
        if("ADMIN".equals(role)){
            return Role.ADMIN;
        }
        throw badRequest("role只能为NORMAL_USER或ADMIN");
    }

    private AccountStatus parseAccountStatus(String status) {
        if(status == null){
            throw badRequest("status不能为空");
        }
        try{
            return AccountStatus.valueOf(status);
        }catch (IllegalArgumentException e){
            throw badRequest("status只能为ACTIVE或DISABLED");
        }
    }

    private AccountStatus defaultAccountStatus(AccountStatus status) {
        return status == null ? AccountStatus.ACTIVE : status;
    }

    private AdminUserListItem toAdminUserListItem(User user) {
        long version = user.getLockVersion() == null ? 0L : user.getLockVersion();
        AccountStatus status = defaultAccountStatus(user.getAccountStatus());
        return AdminUserListItem.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getUserName())
                .role(user.getRole().getRole())
                .roleLabel(user.getRole().getRoleLabel())
                .brand(user.getBrand())
                .status(status.getStatus())
                .version(version)
                .createdAt(user.getSignupDate())
                .build();
    }

    private ApiHttpException badRequest(String message) {
        return new ApiHttpException(HttpStatus.BAD_REQUEST,
                Code.ParamError.getCode(),Code.ParamError.getDesc()+":"+message);
    }

    private void validateAdminUserListRequest(AdminUserListRequest request) {
        if(request.getPage() == null || request.getPage() < 1){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":页码必须从1开始");
        }
        if(request.getNum() == null
                || request.getNum() < 1
                || request.getNum() > 100){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":每页条数必须在1到100之间");
        }

        String keyWord = request.getKeyWord();
        if(keyWord != null){
            keyWord = keyWord.trim();
            if(keyWord.length() > 100){
                throw new BusinessException(Code.ParamError.getCode(),
                        Code.ParamError.getDesc()+":关键字最多100个字符");
            }
            request.setKeyWord(keyWord.isEmpty() ? null : keyWord);
        }

        if(request.getRole() != null
                && !Set.of("NORMAL_USER","ADMIN","SUPER_ADMIN")
                .contains(request.getRole())){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":角色参数错误");
        }
        if(request.getStatus() != null
                && !Set.of("ACTIVE","DISABLED")
                .contains(request.getStatus())){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":账号状态参数错误");
        }

        try{
            request.setOffset(Math.multiplyExact(
                    request.getPage()-1,request.getNum()
            ));
        }catch (ArithmeticException e){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":页码超出允许范围");
        }
    }

    private LocalDate toLocalDate(Object date){
        if(date == null){
            return null;
        }
        if(date instanceof LocalDate localDate){
            return localDate;
        }
        if(date instanceof Date sqlDate){
            return sqlDate.toLocalDate();
        }
        try{
            return LocalDate.parse(date.toString());
        }catch (Exception e){
            return null;
        }
    }

    private BigDecimal getRatio(long count,long total){
        if(total == 0){
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(count).divide(
                BigDecimal.valueOf(total),6,RoundingMode.HALF_UP
        );
    }


}
