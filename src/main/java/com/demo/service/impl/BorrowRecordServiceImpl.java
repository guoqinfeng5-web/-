package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.BorrowRecord;
import com.demo.mapper.BorrowRecordMapper;
import com.demo.service.BorrowRecordService;
import org.springframework.stereotype.Service;

/**
 * 借阅记录 Service 实现（表 {@code borrow_records}）。
 */
@Service
public class BorrowRecordServiceImpl extends ServiceImpl<BorrowRecordMapper, BorrowRecord> implements BorrowRecordService {
}
