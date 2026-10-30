package com.ttdemo.modules.user.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.user.service.UserService;
import com.ttdemo.security.SecurityUtils;
import com.ttdemo.security.perm.Permissions;
import com.ttdemo.security.perm.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台：账号注销审批（消费者权益兜底流程）
 */
@RestController
@RequestMapping("/api/admin/v1/deactivations")
public class AdminDeactivationController {

    private final UserService userService;

    public AdminDeactivationController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequirePerm(Permissions.POINT_VIEW)
    public R<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) Integer status) {
        return R.ok(userService.listDeactivations(pageNum, pageSize, status));
    }

    @PostMapping("/{id}/handle")
    @RequirePerm(Permissions.POINT_ADJUST)
    public R<Void> handle(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        boolean approve = body != null && Boolean.TRUE.equals(body.get("approve"));
        String remark = body == null || body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        if (!approve && (remark == null || remark.isBlank())) {
            throw new BizException(ResultCode.BAD_REQUEST, "驳回注销需填写理由");
        }
        userService.handleDeactivation(id, SecurityUtils.currentUserId(), approve, remark);
        return R.ok();
    }
}
