package com.product.service.impl;

import com.product.MyException.BusinessException;
import com.product.config.BusinessProperties;
import com.product.enums.Code;
import com.product.mapper.OrderMapper;
import com.product.mapper.ProductMapper;
import com.product.pojo.dto.request.Filter;
import com.product.service.OrderService;
import com.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class FilterValidator {
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final BusinessProperties businessProperties;
    public void validate(Filter filter) {
        // 定义null为业务默认
        if(filter.getBrands() == null){
            filter.setBrands(List.of());
        }

        if(filter.getCategories() == null){
            filter.setCategories(List.of());
        }

        if(filter.getStatuses() == null){
            filter.setStatuses(List.of());
        }

        if(filter.getEndTime()==null){
            filter.setEndTime(businessProperties.getTime());
        }
        if(filter.getStartTime()==null){
            filter.setStartTime(filter.getEndTime().minusDays(29));
            if(filter.getStartTime().isBefore(businessProperties.getStartTime())){
                filter.setStartTime(businessProperties.getStartTime());
            }
        }
        if(filter.getEndTime()
                .isBefore(filter.getStartTime()
                )
        ){

            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":开始时间超出结束时间");
        }
        if(filter.getStartTime().isBefore(businessProperties.getStartTime())
                || filter.getEndTime().isAfter(businessProperties.getTime())){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":日期超出允许范围");
        }
        // 种类参数
        if(filter.getCategories()!=null){
            List<String> requestCategories = filter.getCategories()
                    .stream()
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .toList();
            Set<String> categories = productMapper.selectCategories()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toCollection(HashSet::new));
            // set要快一些
            if(!categories.containsAll(requestCategories)){
                throw new BusinessException(Code.ParamError.getCode()
                        ,Code.ParamError.getDesc()+":存在未知种类");
            }
            filter.setCategories(requestCategories);
        }

        // 品牌参数
        if(filter.getBrands()!=null){
            List<String> requestBrands = filter.getBrands()
                    .stream()
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .toList();
            Set<String> brands = productMapper.selectBrands()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toCollection(HashSet::new));
            if(!brands.containsAll(requestBrands)){
                throw new BusinessException(Code.ParamError.getCode()
                        ,Code.ParamError.getDesc()+":存在未知品牌");
            }
            filter.setBrands(requestBrands);
        }


        // 状态参数
        if(filter.getStatuses()!=null){
            Set<String> status = orderMapper.selectStatus()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toCollection(HashSet::new));
            // set要快一些
            if(!status.containsAll(filter.getStatuses())){
                throw new BusinessException(Code.ParamError.getCode()
                        ,Code.ParamError.getDesc()+":存在未知状态");
            }
           filter.setStatuses(
                    filter
                    .getStatuses()
                    .stream()
                    .distinct()
                    .toList());
        }

    }
}
