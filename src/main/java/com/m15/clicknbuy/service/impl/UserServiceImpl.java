package com.m15.clicknbuy.service.impl;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;

import com.m15.clicknbuy.dao.UserDao;
import com.m15.clicknbuy.dto.PasswordDto;
import com.m15.clicknbuy.dto.UserDto;
import com.m15.clicknbuy.entity.CartItem;
import com.m15.clicknbuy.entity.Order;
import com.m15.clicknbuy.entity.OrderItem;
import com.m15.clicknbuy.entity.Product;
import com.m15.clicknbuy.entity.User;
import com.m15.clicknbuy.repository.CartItemRepository;
import com.m15.clicknbuy.repository.OrderItemRepository;
import com.m15.clicknbuy.repository.OrderRepository;
import com.m15.clicknbuy.repository.ProductRepository;
import com.m15.clicknbuy.service.UserService;
import com.m15.clicknbuy.util.OtpSender;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Service
public class UserServiceImpl implements UserService {

	@Autowired
	UserDao userDao;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	CartItemRepository itemRepository;

	@Autowired
	OtpSender otpSender;

	@Autowired
	OrderItemRepository orderItemRepository;

	@Autowired
	OrderRepository orderRepository;

	@Override
	public String registerUser(UserDto userDto, BindingResult result, HttpSession session) {
		if (!userDto.getPassword().equals(userDto.getConfirmPassword()))
			result.rejectValue("confirmPassword", "error.confirmPassword",
					"* Password and Confirm Password Shoulb be Same");
		if (userDao.emailExists(userDto.getEmail()))
			result.rejectValue("email", "error.email", "* Email Should be Unique");
		if (userDao.mobileExists(userDto.getMobile()))
			result.rejectValue("mobile", "error.mobile", "* Mobile Number Should be Unique");

		if (result.hasErrors())
			return "register.html";
		else {
			int otp = new Random().nextInt(100000, 1000000);
			otpSender.sendOtpThruEmail(userDto.getEmail(), otp, userDto.getName());
			otpSender.sendOtpThruMobile(userDto.getMobile(), otp, userDto.getName());
			User user = new User(null, userDto.getName(), userDto.getEmail(), encoder.encode(userDto.getPassword()),
					userDto.getMobile(), userDto.getGender(), otp, false, "ROLE_USER", null);
			userDao.save(user);
			session.setAttribute("success", "Otp Sent Success");
			session.setAttribute("id", user.getId());
			return "redirect:/otp";
		}
	}

	@Override
	public String confirmOtp(Long id, int otp, HttpSession session) {
		User user = userDao.findById(id);
		if (user.getOtp() == otp) {
			if (user.getCreatedTime().plusMinutes(5).isAfter(LocalDateTime.now())) {
				user.setVerified(true);
				user.setOtp(0);
				userDao.save(user);
				session.setAttribute("success", "Account Created Success");
				return "redirect:/login";
			} else {
				session.setAttribute("id", user.getId());
				session.setAttribute("error", "Otp Expired, try resending");
				return "redirect:/otp";
			}
		} else {
			session.setAttribute("id", user.getId());
			session.setAttribute("error", "Invalid OTP , Try Again");
			return "redirect:/otp";
		}
	}

	@Override
	public String resendOtp(Long id, HttpSession session) {
		User user = userDao.findById(id);
		int otp = new Random().nextInt(100000, 1000000);
		user.setOtp(otp);
		otpSender.sendOtpThruEmail(user.getEmail(), otp, user.getName());
		otpSender.sendOtpThruMobile(user.getMobile(), otp, user.getName());
		user.setCreatedTime(LocalDateTime.now());
		userDao.save(user);
		session.setAttribute("success", "Otp Re-sent Success");
		session.setAttribute("id", user.getId());
		return "redirect:/otp";
	}

	@Override
	public String forgotPassword(String email, HttpSession session) {
		Optional<User> opUser = userDao.findByEmail(email);
		if (opUser.isEmpty()) {
			session.setAttribute("error", "No Account with entered Email");
			return "redirect:/forgot-password";
		} else {
			User user = opUser.get();
			int otp = new Random().nextInt(100000, 1000000);
			user.setOtp(otp);
			otpSender.sendOtpThruEmail(user.getEmail(), otp, user.getName());
			user.setCreatedTime(LocalDateTime.now());
			userDao.save(user);
			session.setAttribute("success", "Otp Sent Success");
			session.setAttribute("id", user.getId());
			return "redirect:/reset-password";
		}
	}

	@Override
	public String resetPassword(@Valid PasswordDto passwordDto, BindingResult result, HttpSession session, Long id,
			ModelMap map) {
		if (!passwordDto.getPassword().equals(passwordDto.getConfirmPassword()))
			result.rejectValue("password", "error.password", "* Password and Confirm Password Should be matching");

		if (result.hasErrors()) {
			map.put("id", id);
			return "/reset-password";
		} else {
			User user = userDao.findById(id);
			if (user.getOtp() == passwordDto.getOtp()) {
				if (user.getCreatedTime().plusMinutes(5).isAfter(LocalDateTime.now())) {
					user.setPassword(encoder.encode(passwordDto.getPassword()));
					user.setOtp(0);
					user.setVerified(true);
					userDao.save(user);
					session.setAttribute("success", "Password Reset Success");
					return "redirect:/login";
				} else {
					session.setAttribute("id", user.getId());
					session.setAttribute("error", "Otp expired retry");
					session.setAttribute("id", user.getId());
					return "redirect:/reset-password";
				}
			} else {
				session.setAttribute("id", user.getId());
				session.setAttribute("error", "Invalid OTP Try Again");
				session.setAttribute("id", user.getId());
				return "redirect:/reset-password";
			}
		}
	}

	@Override
	@SuppressWarnings("null")
	public String addToCart(Long id, HttpSession session, Principal principal) {
		Product product = productRepository.findById(id).orElseThrow();
		if (product.getStock() > 0) {
			String email = principal.getName();
			User user = userDao.findByEmail(email).orElseThrow();

			List<CartItem> items = itemRepository.findByUser(user);
			if (items.isEmpty()) {
				CartItem item = new CartItem();
				item.setUser(user);
				item.setProduct(product);
				item.setQuantity(1);
				itemRepository.save(item);
			} else {
				boolean flag = true;
				for (CartItem item : items) {
					if (item.getProduct().getId().equals(product.getId())) {
						item.setQuantity(item.getQuantity() + 1);
						itemRepository.save(item);
						flag = false;
						break;
					}
				}
				if (flag) {
					CartItem item = new CartItem();
					item.setUser(user);
					item.setProduct(product);
					item.setQuantity(1);
					itemRepository.save(item);
				}
			}
			product.setStock(product.getStock() - 1);
			productRepository.save(product);
			session.setAttribute("success", "Product Added to Cart");
			return "redirect:/user/cart";
		} else {
			session.setAttribute("error", "OUT OF STOCK");
			return "redirect:/";
		}
	}

	@Override
	public String viewCart(HttpSession session, Principal principal, ModelMap map) {
		String email = principal.getName();
		User user = userDao.findByEmail(email).orElseThrow();
		List<CartItem> items = itemRepository.findByUser(user);
		if (items.isEmpty()) {
			session.setAttribute("error", "No Products in Cart");
			return "redirect:/";
		} else {
			if (session.getAttribute("success") != null) {
				map.put("success", session.getAttribute("success"));
				session.removeAttribute("success");
			}
			if (session.getAttribute("error") != null) {
				map.put("error", session.getAttribute("error"));
				session.removeAttribute("error");
			}
			map.put("items", items);
			double total = items.stream().mapToDouble(x -> x.getProduct().getPrice() * x.getQuantity()).sum();
			map.put("total", Math.round(total * 100.0) / 100.0); // Round to 2 decimal places
			return "cart.html";
		}
	}

	@Override
	@SuppressWarnings("null")
	public String increase(HttpSession session, Long id, ModelMap map) {
		CartItem item = itemRepository.findById(id).orElseThrow();
		Product product = item.getProduct();
		if (product.getStock() > 0) {
			item.setQuantity(item.getQuantity() + 1);
			itemRepository.save(item);
			product.setStock(product.getStock() - 1);
			productRepository.save(product);
			session.setAttribute("success", "Item Increased Success");
			return "redirect:/user/cart";
		} else {
			session.setAttribute("error", "Not Enough Stock");
			return "redirect:/user/cart";
		}
	}

	@Override
	@SuppressWarnings("null")
	public String decrease(HttpSession session, Long id, ModelMap map) {
		CartItem item = itemRepository.findById(id).orElseThrow();
		Product product = item.getProduct();
		if (item.getQuantity() > 1) {
			item.setQuantity(item.getQuantity() - 1);
			itemRepository.save(item);
			product.setStock(product.getStock() + 1);
			productRepository.save(product);
			session.setAttribute("success", "Item Decreased Success");
			return "redirect:/user/cart";
		} else {
			itemRepository.delete(item);
			session.setAttribute("success", "Item Decreased Success");
			return "redirect:/user/cart";
		}
	}

	@Override
	public String checkout(HttpSession session, Principal principal, ModelMap map) {
		String email = principal.getName();
		User user = userDao.findByEmail(email).orElseThrow();
		List<CartItem> items = itemRepository.findByUser(user);
		if (items.isEmpty()) {
			session.setAttribute("error", "No Products in Cart");
			return "redirect:/";
		}
		double total = items.stream().mapToDouble(x -> x.getProduct().getPrice() * x.getQuantity()).sum();
		double roundedTotal = Math.round(total * 100.0) / 100.0;
		map.put("items", items);
		map.put("user", user);
		map.put("total", roundedTotal);
		return "checkout.html";
	}

	@Override
	@SuppressWarnings("null")
	public String dummyPayment(String address, Principal principal, HttpSession session, ModelMap map) {
		String email = principal.getName();
		User user = userDao.findByEmail(email).orElseThrow();

		List<CartItem> cartItems = itemRepository.findByUser(user);
		if (cartItems.isEmpty()) {
			session.setAttribute("error", "Your cart is empty.");
			return "redirect:/";
		}

		// Create the order
		Order order = new Order();
		order.setUser(user);
		order.setRazorpayOrderId("DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
		order.setRazorpayPaymentId("DEMO-PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
		order.setRazorpaySignature("DEMO_SIGNATURE");
		order.setOrderDate(LocalDateTime.now());
		order.setStatus("PAID");
		order.setDeliveryAddress(address);

		// Build order items
		List<OrderItem> orderItems = cartItems.stream().map(cartItem -> {
			OrderItem orderItem = new OrderItem();
			orderItem.setOrder(order);
			orderItem.setProduct(cartItem.getProduct());
			orderItem.setQuantity(cartItem.getQuantity());
			orderItem.setPrice(cartItem.getProduct().getPrice());
			return orderItem;
		}).toList();

		// Calculate total
		double totalAmount = cartItems.stream()
				.mapToDouble(c -> c.getQuantity() * c.getProduct().getPrice()).sum();
		order.setTotalAmount(totalAmount);

		// Save order and items
		orderRepository.save(order);
		orderItemRepository.saveAll(orderItems);

		// Clear the cart
		itemRepository.deleteAll(cartItems);

		session.setAttribute("success", "Order placed successfully! Order ID: " + order.getRazorpayOrderId());
		return "redirect:/user/orders";
	}

	@Override
	public String viewOrders(Principal principal, HttpSession session, ModelMap map) {
		if (principal == null) {
			return "redirect:/login";
		}

		User user = userDao.findByEmail(principal.getName()).orElseThrow();
		List<Order> orders = orderRepository.findByUser(user);
		map.put("orders", orders);
		return "orders";
	}

}
