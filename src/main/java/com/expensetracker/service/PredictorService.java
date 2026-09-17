package com.expensetracker.service;

import com.expensetracker.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PredictorService {

    private final ExpenseRepository repository;

    public PredictorService(ExpenseRepository repository) {
        this.repository = repository;
    }

    public static class RegressionResult {
        public final double slope;
        public final double intercept;
        public final double rSquared;

        public RegressionResult(double slope, double intercept, double rSquared) {
            this.slope = slope;
            this.intercept = intercept;
            this.rSquared = rSquared;
        }
    }

    public static RegressionResult linearRegression(List<Double> xValues, List<Double> yValues) {
        int n = xValues.size();
        if (n == 0) {
            return new RegressionResult(0.0, 0.0, 0.0);
        }

        double sumX = 0.0;
        double sumY = 0.0;
        for (int i = 0; i < n; i++) {
            sumX += xValues.get(i);
            sumY += yValues.get(i);
        }
        double xMean = sumX / n;
        double yMean = sumY / n;

        double denom = 0.0;
        double num = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = xValues.get(i) - xMean;
            double dy = yValues.get(i) - yMean;
            denom += dx * dx;
            num += dx * dy;
        }

        if (denom == 0.0) {
            return new RegressionResult(0.0, yMean, 0.0);
        }

        double slope = num / denom;
        double intercept = yMean - slope * xMean;

        double totalVariance = 0.0;
        double residualVariance = 0.0;
        for (int i = 0; i < n; i++) {
            double y = yValues.get(i);
            double pred = slope * xValues.get(i) + intercept;
            totalVariance += Math.pow(y - yMean, 2);
            residualVariance += Math.pow(y - pred, 2);
        }

        double rSquared = 1.0;
        if (totalVariance > 0.0) {
            rSquared = Math.max(0.0, Math.min(1.0, 1.0 - (residualVariance / totalVariance)));
        }

        return new RegressionResult(slope, intercept, rSquared);
    }

    public Map<String, Object> predictNextMonth(int monthsBack, Long userId) {
        Map<String, Double> monthlyMap = repository.monthlyTotals(userId);
        if (monthlyMap == null || monthlyMap.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("total_next_month", 0.0);
            empty.put("per_category_next_month", Collections.emptyMap());
            empty.put("historical_months", Collections.emptyList());
            empty.put("historical_values", Collections.emptyList());
            empty.put("predicted_month_label", "Next Month");
            empty.put("trend", "Stable");
            empty.put("trend_pct", 0.0);
            empty.put("confidence_score", 50);
            empty.put("recent_average", 0.0);
            return empty;
        }

        List<String> sortedKeys = new ArrayList<>(monthlyMap.keySet());
        Collections.sort(sortedKeys);

        int lookback = Math.max(2, Math.min(monthsBack, sortedKeys.size()));
        List<String> recentKeys = sortedKeys.subList(sortedKeys.size() - lookback, sortedKeys.size());

        List<Double> yValues = new ArrayList<>();
        List<Double> xValues = new ArrayList<>();
        for (int i = 0; i < recentKeys.size(); i++) {
            xValues.add((double) i);
            yValues.add(Math.max(0.0, monthlyMap.getOrDefault(recentKeys.get(i), 0.0)));
        }

        RegressionResult reg = linearRegression(xValues, yValues);
        int nextX = xValues.size();
        double regressedTotal = reg.slope * nextX + reg.intercept;

        double sumY = 0.0;
        for (double v : yValues) sumY += v;
        double recentAvg = yValues.isEmpty() ? 0.0 : sumY / yValues.size();

        double totalNext = regressedTotal >= 0.0 ? regressedTotal : recentAvg;
        totalNext = Math.max(0.0, totalNext);

        // Trend calculation
        double lastActual = yValues.isEmpty() ? 0.0 : yValues.get(yValues.size() - 1);
        double trendPct = 0.0;
        if (lastActual > 0) {
            trendPct = Math.round(((totalNext - lastActual) / lastActual) * 1000.0) / 10.0;
        }

        String trend;
        if (trendPct > 3.0) {
            trend = "Increasing";
        } else if (trendPct < -3.0) {
            trend = "Decreasing";
        } else {
            trend = "Stable";
        }

        int confidenceScore = (int) Math.max(40, Math.min(95, Math.round(reg.rSquared * 100.0)));

        // Next month label
        String lastYm = recentKeys.get(recentKeys.size() - 1);
        String nextMonthLabel;
        try {
            YearMonth ym = YearMonth.parse(lastYm);
            nextMonthLabel = ym.plusMonths(1).toString();
        } catch (Exception e) {
            nextMonthLabel = "Next Month";
        }

        // Per-category projection
        Map<String, Map<String, Double>> byCat = repository.monthlyTotalsByCategory(userId);
        Map<String, Double> perCategoryNext = new LinkedHashMap<>();

        if (byCat != null && !byCat.isEmpty()) {
            Set<String> categories = new TreeSet<>();
            for (String k : recentKeys) {
                Map<String, Double> monthMap = byCat.get(k);
                if (monthMap != null) {
                    categories.addAll(monthMap.keySet());
                }
            }

            for (String cat : categories) {
                List<Double> yCat = new ArrayList<>();
                double sumCat = 0.0;
                for (String k : recentKeys) {
                    double val = byCat.getOrDefault(k, Collections.emptyMap()).getOrDefault(cat, 0.0);
                    yCat.add(val);
                    sumCat += val;
                }

                if (sumCat == 0.0) continue;

                RegressionResult catReg = linearRegression(xValues, yCat);
                double catPred = catReg.slope * nextX + catReg.intercept;
                if (catPred < 0.0) {
                    catPred = sumCat / yCat.size();
                }
                perCategoryNext.put(cat, Math.round(Math.max(0.0, catPred) * 100.0) / 100.0);
            }
        }

        if (perCategoryNext.isEmpty() && byCat != null && !byCat.isEmpty()) {
            String lastKey = recentKeys.get(recentKeys.size() - 1);
            Map<String, Double> lastCats = byCat.getOrDefault(lastKey, Collections.emptyMap());
            double lastTotal = 0.0;
            for (double v : lastCats.values()) lastTotal += v;
            if (lastTotal <= 0.0) lastTotal = 1.0;

            for (Map.Entry<String, Double> entry : lastCats.entrySet()) {
                double share = totalNext * (entry.getValue() / lastTotal);
                perCategoryNext.put(entry.getKey(), Math.round(share * 100.0) / 100.0);
            }

            perCategoryNext = perCategoryNext.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
        }

        List<Double> roundedYValues = new ArrayList<>();
        for (Double v : yValues) {
            roundedYValues.add(Math.round(v * 100.0) / 100.0);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("total_next_month", Math.round(totalNext * 100.0) / 100.0);
        output.put("per_category_next_month", perCategoryNext);
        output.put("historical_months", recentKeys);
        output.put("historical_values", roundedYValues);
        output.put("predicted_month_label", nextMonthLabel);
        output.put("trend", trend);
        output.put("trend_pct", trendPct);
        output.put("confidence_score", confidenceScore);
        output.put("recent_average", Math.round(recentAvg * 100.0) / 100.0);

        return output;
    }

    public Map<String, Object> simulateSavingsTarget(double targetSavingsPct, Long userId) {
        Map<String, Object> forecast = predictNextMonth(6, userId);
        double totalPred = ((Number) forecast.get("total_next_month")).doubleValue();
        double targetSpend = Math.max(0.0, totalPred * (1.0 - (targetSavingsPct / 100.0)));
        double savingsAmount = totalPred - targetSpend;

        Map<String, Double> discretionaryWeights = Map.of(
                "Shopping", 0.40,
                "Entertainment", 0.30,
                "Food", 0.20,
                "Travel", 0.10
        );

        @SuppressWarnings("unchecked")
        Map<String, Double> perCat = (Map<String, Double>) forecast.get("per_category_next_month");
        Map<String, Map<String, Double>> recommendations = new LinkedHashMap<>();

        if (perCat != null) {
            for (Map.Entry<String, Double> entry : perCat.entrySet()) {
                String cat = entry.getKey();
                double currentPred = entry.getValue();
                double weight = discretionaryWeights.getOrDefault(cat, 0.05);
                double suggestedCut = Math.min(currentPred * 0.4, savingsAmount * weight);
                double newTarget = Math.max(0.0, currentPred - suggestedCut);

                Map<String, Double> rec = new LinkedHashMap<>();
                rec.put("current_forecast", currentPred);
                rec.put("suggested_cap", Math.round(newTarget * 100.0) / 100.0);
                rec.put("potential_savings", Math.round(suggestedCut * 100.0) / 100.0);
                recommendations.put(cat, rec);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current_forecast_total", totalPred);
        result.put("target_spend", Math.round(targetSpend * 100.0) / 100.0);
        result.put("target_savings_amount", Math.round(savingsAmount * 100.0) / 100.0);
        result.put("recommendations", recommendations);

        return result;
    }
}
