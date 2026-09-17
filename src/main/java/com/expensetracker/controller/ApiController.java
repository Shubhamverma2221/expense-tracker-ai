package com.expensetracker.controller;

import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.service.ChatBotService;
import com.expensetracker.service.FinancialIntelligenceService;
import com.expensetracker.service.PredictorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ChatBotService chatBotService;
    private final FinancialIntelligenceService financialService;
    private final PredictorService predictorService;
    private final ExpenseRepository repository;

    public ApiController(ChatBotService chatBotService,
                         FinancialIntelligenceService financialService,
                         PredictorService predictorService,
                         ExpenseRepository repository) {
        this.chatBotService = chatBotService;
        this.financialService = financialService;
        this.predictorService = predictorService;
        this.repository = repository;
    }

    private Long getUserId(HttpSession session) {
        if (session != null && session.getAttribute("user_id") != null) {
            try {
                return Long.valueOf(String.valueOf(session.getAttribute("user_id")));
            } catch (Exception ignored) {}
        }
        return 1L;
    }

    private String getCurrency(HttpSession session) {
        if (session != null && session.getAttribute("user_currency") != null) {
            return String.valueOf(session.getAttribute("user_currency"));
        }
        return "₹";
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody(required = false) Map<String, Object> body,
                                                    HttpSession session) {
        String message = body != null && body.get("message") != null ? String.valueOf(body.get("message")) : "";
        Long uid = getUserId(session);
        String curr = getCurrency(session);

        String reply = chatBotService.respond(message, uid, curr);
        return ResponseEntity.ok(Map.of("response", reply));
    }

    @PostMapping("/affordability")
    public ResponseEntity<Map<String, Object>> affordability(@RequestBody(required = false) Map<String, Object> body,
                                                             HttpSession session) {
        String itemName = body != null && body.get("item_name") != null ? String.valueOf(body.get("item_name")) : "Item";
        double amount = 0.0;
        if (body != null && body.get("amount") != null) {
            try {
                amount = Double.parseDouble(String.valueOf(body.get("amount")));
            } catch (Exception ignored) {}
        }
        String category = body != null && body.get("category") != null ? String.valueOf(body.get("category")) : null;
        Long uid = getUserId(session);

        Map<String, Object> result = financialService.checkAffordability(uid, itemName, amount, category);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health-score")
    public ResponseEntity<Map<String, Object>> healthScore(HttpSession session) {
        Long uid = getUserId(session);
        Map<String, Object> scoreData = financialService.calculateHealthScore(uid);
        return ResponseEntity.ok(scoreData);
    }

    @PostMapping("/what-if")
    public ResponseEntity<Map<String, Object>> whatIf(@RequestBody(required = false) Map<String, Object> body,
                                                      HttpSession session) {
        String scenarioType = body != null && body.get("scenario_type") != null ? String.valueOf(body.get("scenario_type")) : "major_purchase";
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body != null && body.get("params") instanceof Map ? (Map<String, Object>) body.get("params") : Map.of();
        Long uid = getUserId(session);

        Map<String, Object> result = financialService.simulateWhatIf(uid, scenarioType, params);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/money-leaks")
    public ResponseEntity<Map<String, Object>> moneyLeaks(HttpSession session) {
        Long uid = getUserId(session);
        Map<String, Object> leaks = financialService.detectMoneyLeaks(uid, 300.0);
        return ResponseEntity.ok(leaks);
    }

    @GetMapping("/cashflow-collision")
    public ResponseEntity<List<Map<String, Object>>> cashflowCollision(HttpSession session) {
        Long uid = getUserId(session);
        List<Map<String, Object>> collisions = financialService.detectCashflowCollisions(uid);
        return ResponseEntity.ok(collisions);
    }

    @PostMapping("/parse-receipt")
    public ResponseEntity<Map<String, Object>> parseReceipt(@RequestBody(required = false) Map<String, Object> body) {
        String text = body != null && body.get("text") != null ? String.valueOf(body.get("text")) : "";
        Map<String, Object> parsed = financialService.parseReceiptText(text);
        return ResponseEntity.ok(parsed);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(@RequestParam(name = "period", defaultValue = "month") String period,
                                                       HttpSession session) {
        Long uid = getUserId(session);
        Map<String, Object> summaryData = repository.getSummary(period, uid);
        return ResponseEntity.ok(summaryData);
    }

    @GetMapping("/predict")
    public ResponseEntity<Map<String, Object>> predict(@RequestParam(name = "months", defaultValue = "6") int months,
                                                       HttpSession session) {
        Long uid = getUserId(session);
        Map<String, Object> forecast = predictorService.predictNextMonth(months, uid);
        return ResponseEntity.ok(forecast);
    }
}
