package com.ttdemo.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ttdemo.modules.auth.entity.SmsCode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsCodeMapper extends BaseMapper<SmsCode> {
}
