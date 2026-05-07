package com.m15.clicknbuy.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OtpSender {

	@Value("${twilio.sid:}")
	String TWILIO_ACCOUNT_SID;

	@Value("${twilio.auth.token:}")
	String TWILIO_AUTH_TOKEN;

	@Value("${twilio.mobile:}")
	String TWILIO_MOBILE;

	@Autowired
	JavaMailSender mailSender;

	@Autowired
	TemplateEngine templateEngine;

	@Async
	@SuppressWarnings("null")
	public void sendOtpThruEmail(String email, int otp, String name) {
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message);
		try {
			helper.setFrom("noreply@clicknbuy.com", "Clicknbuy");
			helper.setTo(email);
			helper.setSubject("Otp for Creating account with ClickNBuy");
			Context context = new Context();
			context.setVariable("name", name);
			context.setVariable("otp", otp);
			String emailMessage = templateEngine.process("email-template.html", context);
			helper.setText(emailMessage, true);
			mailSender.send(message);
			log.info("OTP email sent to {}", email);
		} catch (Exception e) {
			log.warn("Email sending failed. OTP for {} is: {}", email, otp);
		}
	}

	@Async
	public void sendOtpThruMobile(Long mobile, int otp, String name) {
		if (TWILIO_ACCOUNT_SID == null || TWILIO_ACCOUNT_SID.isBlank()
				|| TWILIO_AUTH_TOKEN == null || TWILIO_AUTH_TOKEN.isBlank()
				|| TWILIO_MOBILE == null || TWILIO_MOBILE.isBlank()) {
			log.warn("Twilio not configured. SMS skipped. OTP for +91{} is: {}", mobile, otp);
			return;
		}
		try {
			Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
			Message.creator(new PhoneNumber("+91" + mobile), new PhoneNumber(TWILIO_MOBILE),
					"Hello " + name + " Thanks for creating account your OTP is " + otp).create();
			log.info("OTP SMS sent to +91{}", mobile);
		} catch (Exception e) {
			log.warn("SMS sending failed. OTP for +91{} is: {}", mobile, otp);
		}
	}

}
