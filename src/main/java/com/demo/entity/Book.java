package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 书籍表实体：books。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("books")
public class Book {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 书名 */
    @TableField("title")
    private String title;

    /** 作者 */
    @TableField("author")
    private String author;

    /** 分类 */
    @TableField("category")
    private String category;

    /** 价格 */
    @TableField("price")
    private BigDecimal price;

    /** 评分 */
    @TableField("score")
    private Double score;

    /** 书籍详细简介（AI分析的关键素材） */
    @TableField("summary")
    private String summary;

    /** 标签，逗号隔开 */
    @TableField("tags")
    private String tags;

    /** 库存 */
    @TableField("stock")
    private Integer stock;

    /** 借阅次数 */
    @TableField("borrow_count")
    private int borrowCount;

    /** 入库时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}

