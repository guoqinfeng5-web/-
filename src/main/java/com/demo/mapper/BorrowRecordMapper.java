package com.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.entity.BorrowRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 借阅记录 Mapper。
 */
@Mapper
public interface BorrowRecordMapper extends BaseMapper<BorrowRecord> {
}
