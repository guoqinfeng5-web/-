package com.demo.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.entity.Book;
import com.demo.service.BookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BookController.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;

    @Test
    void page_shouldReturnOkResult() throws Exception {
        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<Book> wrapper = mock(LambdaQueryChainWrapper.class);

        Page<Book> page = Page.of(1, 10);
        page.setRecords(List.of(Book.builder().id(1L).title("T1").author("A1").category("C1").build()));

        when(bookService.lambdaQuery()).thenReturn(wrapper);
        when(wrapper.like(anyBoolean(), any(), any())).thenReturn(wrapper);
        when(wrapper.page(any(Page.class))).thenReturn(page);

        mockMvc.perform(get("/api/books/page")
                        .queryParam("pageNum", "1")
                        .queryParam("pageSize", "10")
                        .queryParam("title", "Java")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":200")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"records\"")));
    }
}

