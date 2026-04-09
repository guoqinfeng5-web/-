package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.UserActivity;
import com.demo.mapper.UserActivityMapper;
import com.demo.service.UserActivityService;
import org.springframework.stereotype.Service;

/**
 * 用户活动记录 Service 实现。
 */
@Service
public class UserActivityServiceImpl extends ServiceImpl<UserActivityMapper, UserActivity> implements UserActivityService {
}

