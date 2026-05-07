package com.m15.clicknbuy.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import com.m15.clicknbuy.dto.ProductDto;
import com.m15.clicknbuy.entity.Product;
import com.m15.clicknbuy.repository.CartItemRepository;
import com.m15.clicknbuy.repository.OrderItemRepository;
import com.m15.clicknbuy.repository.ProductRepository;
import com.m15.clicknbuy.service.AdminService;
import com.m15.clicknbuy.util.CloudinaryHelper;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Service
public class AdminServiceImpl implements AdminService {

	@Autowired
	ProductRepository productRepository;

	@Autowired
	CartItemRepository cartItemRepository;

	@Autowired
	OrderItemRepository orderItemRepository;

	@Autowired
	CloudinaryHelper cloudinaryHelper;

	@Override
	public String addProduct(@Valid ProductDto productDto, BindingResult result, HttpSession session) {
		if (productDto.getImage().isEmpty())
			result.rejectValue("image", "error.image", "* Image is Required");
		if (productRepository.existsByName(productDto.getName()))
			result.rejectValue("name", "error.name", "* Product Already Exists");

		if (result.hasErrors())
			return "add-product.html";
		else {
			Product product = new Product(null, productDto.getName(), productDto.getPrice(), productDto.getStock(),
					productDto.getDescription(), cloudinaryHelper.saveToCloudinary(productDto.getImage()),
					productDto.getCategory(), null);
			productRepository.save(product);
			session.setAttribute("success", "Product Added Succes");
			return "redirect:/";
		}

	}

	@Override
	@SuppressWarnings("null")
	public String deleteProduct(Long id, HttpSession session) {
		productRepository.findById(id).ifPresent(product -> {
			// Delete dependent cart items first
			cartItemRepository.deleteAll(cartItemRepository.findByProduct(product));
			// Nullify product reference in order items (keep history)
			orderItemRepository.findByProduct(product).forEach(orderItem -> {
				orderItem.setProduct(null);
				orderItemRepository.save(orderItem);
			});
			cloudinaryHelper.deleteFromCloudinary(product.getImageLink());
			productRepository.deleteById(id);
		});
		session.setAttribute("success", "Product Deleted Success");
		return "redirect:/";
	}

	@Override
	@SuppressWarnings("null")
	public String editProduct(Long id, ModelMap map) {
		Product product = productRepository.findById(id).orElseThrow();
		map.put("product", product);
		return "edit.html";
	}

	@Override
	@SuppressWarnings("null")
	public String updateProduct(Product product, MultipartFile image, HttpSession session) {
		Product oldProduct = productRepository.findById(product.getId()).orElse(null);
		if (image.isEmpty()) {
			if (oldProduct != null) {
				product.setImageLink(oldProduct.getImageLink());
			}
		} else {
			if (oldProduct != null) {
				cloudinaryHelper.deleteFromCloudinary(oldProduct.getImageLink());
			}
			product.setImageLink(cloudinaryHelper.saveToCloudinary(image));
		}
		productRepository.save(product);
		session.setAttribute("success", "Product Updated Success");
		return "redirect:/";
	}

}
