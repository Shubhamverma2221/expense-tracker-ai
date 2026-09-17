package com.expensetracker.controller;

import com.expensetracker.config.FlashHelper;
import com.expensetracker.model.Expense;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.service.CategorizerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Controller
public class ExpenseController {

    private final ExpenseRepository repository;
    private final CategorizerService categorizerService;

    public ExpenseController(ExpenseRepository repository, CategorizerService categorizerService) {
        this.repository = repository;
        this.categorizerService = categorizerService;
    }

    @GetMapping("/add")
    public String addPage(Model model) {
        model.addAttribute("title", "Add Expense");
        model.addAttribute("today", LocalDate.now().toString());
        return "add";
    }

    @PostMapping("/add")
    public String handleAdd(@RequestParam(name = "amount", defaultValue = "0") double amount,
                            @RequestParam(name = "description", defaultValue = "") String description,
                            @RequestParam(name = "date", required = false) String dateIso,
                            @RequestParam(name = "category", defaultValue = "") String category,
                            @RequestParam(name = "payment_method", defaultValue = "UPI") String paymentMethod,
                            @RequestParam(name = "notes", defaultValue = "") String notes,
                            HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        String trimmedDesc = description.trim();

        if (amount <= 0 || trimmedDesc.isBlank()) {
            FlashHelper.flash(session, "Please specify a valid transaction amount and description.", "danger");
            return "redirect:/add";
        }

        String finalDate = (dateIso != null && !dateIso.isBlank()) ? dateIso : LocalDate.now().toString();
        String finalCat = (category != null && !category.isBlank()) ? category.trim() : categorizerService.categorize(trimmedDesc);

        Long expenseId = repository.addExpense(uid, finalDate, amount, trimmedDesc, finalCat, paymentMethod, notes.trim(), "");

        String email = (String) session.getAttribute("user_email");
        repository.logAudit(String.format("Logged Expense #%d (%s)", expenseId, finalCat), uid, email, "127.0.0.1");

        String curr = session.getAttribute("user_currency") != null ? String.valueOf(session.getAttribute("user_currency")) : "₹";
        FlashHelper.flash(session, String.format("Expense of %s%.2f logged under %s!", curr, amount, finalCat), "success");

        return "redirect:/dashboard";
    }

    @GetMapping({"/expenses", "/list"})
    public String listExpenses(@RequestParam(name = "category", required = false) String category,
                               @RequestParam(name = "payment_method", required = false) String paymentMethod,
                               @RequestParam(name = "start", required = false) String start,
                               @RequestParam(name = "end", required = false) String end,
                               @RequestParam(name = "q", required = false) String query,
                               @RequestParam(name = "sort", defaultValue = "date_desc") String sortBy,
                               @RequestParam(name = "page", defaultValue = "1") int page,
                               HttpSession session,
                               Model model) {
        Long uid = (Long) session.getAttribute("user_id");
        int limit = 20;
        int currentPage = Math.max(1, page);
        int offset = (currentPage - 1) * limit;

        int totalCount = repository.countExpenses(uid, start, end, category, query, paymentMethod);
        List<Expense> expenses = repository.listExpenses(uid, start, end, category, query, paymentMethod, sortBy, limit, offset);

        int totalPages = Math.max(1, (totalCount + limit - 1) / limit);

        model.addAttribute("title", "Expense Directory");
        model.addAttribute("expenses", expenses);
        model.addAttribute("page", currentPage);
        model.addAttribute("current_page", currentPage);
        model.addAttribute("limit", limit);
        model.addAttribute("total_pages", totalPages);
        model.addAttribute("total_count", totalCount);
        model.addAttribute("query", query != null ? query : "");
        model.addAttribute("search_query", query != null ? query : "");
        model.addAttribute("selected_category", category != null ? category : "all");
        model.addAttribute("selected_payment", paymentMethod != null ? paymentMethod : "all");
        model.addAttribute("start_date", start != null ? start : "");
        model.addAttribute("end_date", end != null ? end : "");
        model.addAttribute("sort_by", sortBy);

        return "list";
    }

    @PostMapping("/expenses/delete/{expenseId}")
    public String deleteExpense(@PathVariable("expenseId") Long expenseId,
                                HttpServletRequest request,
                                HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        repository.deleteExpense(expenseId, uid);

        String email = (String) session.getAttribute("user_email");
        repository.logAudit("Deleted Expense #" + expenseId, uid, email, "127.0.0.1");

        FlashHelper.flash(session, "Expense transaction deleted.", "info");

        String referrer = request.getHeader("Referer");
        if (referrer != null && !referrer.isBlank()) {
            return "redirect:" + referrer;
        }
        return "redirect:/list";
    }

    @GetMapping("/export/csv")
    public void exportCsv(HttpServletResponse response, HttpSession session) throws IOException {
        Long uid = (Long) session.getAttribute("user_id");
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=my_expenses.csv");
        repository.exportCsv(uid, response.getWriter());
    }

    @GetMapping("/export/json")
    @ResponseBody
    public String exportJson(HttpServletResponse response, HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        response.setContentType("application/json");
        response.setHeader("Content-Disposition", "attachment; filename=my_expenses.json");
        return repository.exportJson(uid);
    }
}
