package com.m15.clicknbuy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.m15.clicknbuy.entity.OrderItem;
import com.m15.clicknbuy.entity.Product;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	List<OrderItem> findByProduct(Product product);

}
