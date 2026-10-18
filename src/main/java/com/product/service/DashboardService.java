package com.product.service;

import com.product.pojo.dto.response.DashboardMetaResponse;
import com.product.pojo.po.User;

public interface DashboardService {
    DashboardMetaResponse getDashboardMeta(User user);
}
