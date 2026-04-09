package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.Book;
import com.demo.mapper.BookMapper;
import com.demo.service.BookService;
import org.springframework.stereotype.Service;

/**
 * 书籍 Service 实现。
 */
@Service
public class BookServiceImpl extends ServiceImpl<BookMapper, Book> implements BookService {
}

