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
 * 借阅记录实体，映射表 {@code borrow_records}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("borrow_records")
public class BorrowRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("book_id")
    private Long bookId;

    @TableField("user_id")
    private Long userId;

    @TableField("borrow_time")
    private LocalDateTime borrowTime;

    /** 未归还时为 null */
    @TableField("return_time")
    private LocalDateTime returnTime;
}
