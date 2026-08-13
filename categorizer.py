import json
import os
from typing import Dict, List, Optional, Tuple


DEFAULT_RULES: Dict[str, List[str]] = {
    "Food": [
        "food", "meal", "restaurant", "cafe", "grocer", "pizza", "burger", "coffee", "tea", "chai",
        "starbucks", "swiggy", "zomato", "blinkit", "zepto", "instamart", "lunch", "dinner", "breakfast",
        "snacks", "bakery", "mcdonalds", "kfc", "dominos", "subway", "bar", "pub", "biryani", "supermarket"
    ],
    "Travel": [
        "uber", "ola", "rapido", "taxi", "cab", "bus", "train", "flight", "fuel", "petrol", "diesel",
        "metro", "toll", "parking", "auto", "railway", "airline", "indigo", "air india", "car service"
    ],
    "Shopping": [
        "amazon", "flipkart", "myntra", "mall", "shop", "clothes", "electronics", "shoes", "zara",
        "h&m", "book", "gadget", "apple", "samsung", "retail", "store", "fashion", "online order"
    ],
    "Bills": [
        "electric", "electricity", "water", "wifi", "internet", "broadband", "mobile", "recharge", "phone",
        "rent", "maintenance", "gas", "cylinder", "utility", "insurance", "emi", "loan", "tax", "subscription"
    ],
    "Entertainment": [
        "movie", "cinema", "netflix", "spotify", "prime", "hotstar", "youtube", "game", "gaming", "steam",
        "playstation", "xbox", "concert", "theatre", "bookmyshow", "club", "party", "outing", "event"
    ],
    "Health": [
        "doctor", "pharmacy", "medicine", "hospital", "clinic", "gym", "fitness", "supplement",
        "dental", "dentist", "lab", "test", "medication", "optical", "glasses", "therapy"
    ],
    "Education": [
        "course", "book", "tuition", "udemy", "coursera", "college", "school", "exam", "cert", "training"
    ],
    "Investments": [
        "stock", "mutual fund", "sip", "crypto", "zerodha", "groww", "gold", "deposit", "savings"
    ],
    "Other": [],
}

CATEGORY_METADATA: Dict[str, Dict[str, str]] = {
    "Food": {"icon": "🍔", "color": "#f59e0b", "bg": "rgba(245, 158, 11, 0.15)"},
    "Travel": {"icon": "🚕", "color": "#3b82f6", "bg": "rgba(59, 130, 246, 0.15)"},
    "Shopping": {"icon": "🛍️", "color": "#ec4899", "bg": "rgba(236, 72, 153, 0.15)"},
    "Bills": {"icon": "🧾", "color": "#ef4444", "bg": "rgba(239, 68, 68, 0.15)"},
    "Entertainment": {"icon": "🎬", "color": "#8b5cf6", "bg": "rgba(139, 92, 246, 0.15)"},
    "Health": {"icon": "🩺", "color": "#10b981", "bg": "rgba(16, 185, 129, 0.15)"},
    "Education": {"icon": "📚", "color": "#06b6d4", "bg": "rgba(6, 182, 212, 0.15)"},
    "Investments": {"icon": "📈", "color": "#14b8a6", "bg": "rgba(20, 184, 166, 0.15)"},
    "Other": {"icon": "📦", "color": "#6b7280", "bg": "rgba(107, 114, 128, 0.15)"},
}


class CategoryRules:
    def __init__(self, path: str = "categories.json") -> None:
        self.path = path
        self._rules = self._load_or_default()

    def _load_or_default(self) -> Dict[str, List[str]]:
        if os.path.exists(self.path):
            try:
                with open(self.path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    rules: Dict[str, List[str]] = {}
                    for category, keywords in data.items():
                        rules[str(category)] = [str(k).lower() for k in keywords]
                    # Ensure all default categories exist
                    for k, v in DEFAULT_RULES.items():
                        if k not in rules:
                            rules[k] = [kw.lower() for kw in v]
                    return rules
            except Exception:
                pass
        return {k: [kw.lower() for kw in v] for k, v in DEFAULT_RULES.items()}

    def save(self) -> None:
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(self._rules, f, indent=2, ensure_ascii=False)

    def get_rules(self) -> Dict[str, List[str]]:
        return self._rules

    def categorize(self, description: str) -> str:
        text = description.lower()
        best_category = "Other"
        best_match_count = 0
        for category, keywords in self._rules.items():
            match_count = sum(1 for kw in keywords if kw and kw in text)
            if match_count > best_match_count:
                best_match_count = match_count
                best_category = category
        return best_category

    def get_meta(self, category: str) -> Dict[str, str]:
        return CATEGORY_METADATA.get(category, {"icon": "🏷️", "color": "#9ca3af", "bg": "rgba(156, 163, 175, 0.15)"})

    def add_keyword(self, category: str, keyword: str) -> None:
        if category not in self._rules:
            self._rules[category] = []
        kw_lower = keyword.lower()
        if kw_lower not in self._rules[category]:
            self._rules[category].append(kw_lower)

    def remove_keyword(self, category: str, keyword: str) -> None:
        if category in self._rules:
            kw_lower = keyword.lower()
            self._rules[category] = [k for k in self._rules[category] if k != kw_lower]
