package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户活动记录实体：user_activities。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_activities")
public class UserActivity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("book_id")
    private Long bookId;

    /**
     * 行为类型: 1-查看, 2-收藏, 3-借阅
     */
    @TableField("activity_type")
    private Integer activityType;

    @TableField("create_time")
    private LocalDateTime createTime;
}

