package com.ttdemo.modules.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 20, message = "昵称最长 20 个字符") String nickname,
        @Size(max = 255, message = "头像地址过长") String avatar
) {
}
