package com.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.entity.Book;
import org.apache.ibatis.annotations.Mapper;

/**
 * 书籍 Mapper。
 */
@Mapper
public interface BookMapper extends BaseMapper<Book> {
}

