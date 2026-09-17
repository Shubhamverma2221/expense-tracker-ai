package com.expensetracker.controller;

import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.service.CategorizerService;
import com.expensetracker.service.PredictorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

@Controller
public class AnalyticsController {

    private final ExpenseRepository repository;
    private final CategorizerService categorizerService;
    private final PredictorService predictorService;

    public AnalyticsController(ExpenseRepository repository,
                               CategorizerService categorizerService,
                               PredictorService predictorService) {
        this.repository = repository;
        this.categorizerService = categorizerService;
        this.predictorService = predictorService;
    }

    @GetMapping({"/analytics", "/summary"})
    public String analyticsPage(@RequestParam(name = "period", defaultValue = "month") String period,
                                HttpSession session,
                                Model model) {
        Long uid = (Long) session.getAttribute("user_id");

        Map<String, Object> summary = repository.getSummary(period, uid);
        Map<String, Double> weekdayDist = repository.getWeekdaySpendingDistribution(uid);

        @SuppressWarnings("unchecked")
        Map<String, Double> byCat = (Map<String, Double>) summary.getOrDefault("by_category", Collections.emptyMap());
        List<String> catLabels = new ArrayList<>(byCat.keySet());
        List<Double> catValues = new ArrayList<>(byCat.values());
        List<String> catColors = new ArrayList<>();
        for (String c : catLabels) {
            Map<String, String> meta = categorizerService.getMeta(c);
            catColors.add(meta.getOrDefault("color", "#6366f1"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Double> byPay = (Map<String, Double>) summary.getOrDefault("by_payment", Collections.emptyMap());
        List<String> payLabels = new ArrayList<>(byPay.keySet());
        List<Double> payValues = new ArrayList<>(byPay.values());

        List<String> weekdayLabels = new ArrayList<>(weekdayDist.keySet());
        List<Double> weekdayValues = new ArrayList<>(weekdayDist.values());

        model.addAttribute("title", "Spending Analytics & Trends");
        model.addAttribute("summary", summary);
        model.addAttribute("period", period);
        model.addAttribute("weekday_dist", weekdayDist);
        model.addAttribute("cat_labels", catLabels);
        model.addAttribute("cat_values", catValues);
        model.addAttribute("cat_colors", catColors);
        model.addAttribute("pay_labels", payLabels);
        model.addAttribute("pay_values", payValues);
        model.addAttribute("weekday_labels", weekdayLabels);
        model.addAttribute("weekday_values", weekdayValues);

        return "analytics";
    }

    @GetMapping("/predict")
    public String predictPage(@RequestParam(name = "months", defaultValue = "6") int months,
                              HttpSession session,
                              Model model) {
        Long uid = (Long) session.getAttribute("user_id");
        Map<String, Object> forecast = predictorService.predictNextMonth(months, uid);

        model.addAttribute("title", "AI Forecast & Predictions");
        model.addAttribute("months", months);
        model.addAttribute("forecast", forecast);

        return "predict";
    }
}
