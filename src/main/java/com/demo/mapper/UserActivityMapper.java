package com.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.entity.UserActivity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户活动记录 Mapper。
 */
@Mapper
public interface UserActivityMapper extends BaseMapper<UserActivity> {
}

