package com.greenrelease.api;

import com.greenrelease.api.controller.OrderController;
import com.greenrelease.core.model.Order;
import com.greenrelease.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for order endpoints
 */
@WebMvcTest(OrderController.class)
@ContextConfiguration(classes = TestConfiguration.class)
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    public void testGetAllOrders() throws Exception {
        List<Order> orders = Arrays.asList(
                new Order(1, 1, "Laptop", 999.99, "COMPLETED"),
                new Order(2, 1, "Mouse", 29.99, "PENDING")
        );
        when(orderService.getAllOrders()).thenReturn(orders);

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", equalTo(1)))
                .andExpect(jsonPath("$[0].product", equalTo("Laptop")))
                .andExpect(jsonPath("$[1].id", equalTo(2)))
                .andExpect(jsonPath("$[1].product", equalTo("Mouse")));
    }

    @Test
    public void testGetOrderById() throws Exception {
        Order order = new Order(1, 1, "Laptop", 999.99, "COMPLETED");
        when(orderService.getOrderById(1)).thenReturn(order);

        mockMvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(1)))
                .andExpect(jsonPath("$.userId", equalTo(1)))
                .andExpect(jsonPath("$.product", equalTo("Laptop")))
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")));
    }

    @Test
    public void testGetOrderByIdNotFound() throws Exception {
        when(orderService.getOrderById(999)).thenReturn(null);

        mockMvc.perform(get("/orders/999"))
                .andExpect(status().isNotFound());
    }
}
