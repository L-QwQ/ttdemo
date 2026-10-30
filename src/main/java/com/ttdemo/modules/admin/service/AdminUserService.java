package com.ttdemo.modules.admin.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.admin.dto.AdminStatsVO;
import com.ttdemo.modules.admin.dto.AdminUserQuery;
import com.ttdemo.modules.user.dto.UserVO;

public interface AdminUserService {

    PageResult<UserVO> page(AdminUserQuery query);

    UserVO detail(Long id);

    void updateStatus(Long operatorId, Long userId, Integer status);

    void updateRole(Long operatorId, Long userId, String role);

    void resetPassword(Long userId, String newPassword);

    void delete(Long operatorId, Long userId);

    AdminStatsVO stats();
}
