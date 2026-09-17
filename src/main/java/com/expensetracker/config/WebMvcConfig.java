package com.expensetracker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**", "/**")
                .addResourceLocations("classpath:/static/", "file:./static/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                HttpSession session = request.getSession(false);
                if (session == null || session.getAttribute("user_id") == null) {
                    if (session != null) {
                        FlashHelper.flash(session, "Please log in or try the instant demo to access this feature.", "warning");
                    }
                    String next = request.getRequestURI();
                    response.sendRedirect("/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
                    return false;
                }
                return true;
            }
        }).addPathPatterns(
                "/dashboard",
                "/decision-studio",
                "/add",
                "/expenses/**",
                "/list",
                "/budgets/**",
                "/recurring/**",
                "/analytics",
                "/summary",
                "/predict",
                "/chat",
                "/notifications/**",
                "/profile",
                "/settings/**",
                "/export/**"
        );

        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                HttpSession session = request.getSession(false);
                if (session == null || !"admin".equals(session.getAttribute("user_role"))) {
                    if (session != null) {
                        FlashHelper.flash(session, "Administrator access required for this area.", "danger");
                    }
                    response.sendRedirect("/dashboard");
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/admin/**");
    }
}
