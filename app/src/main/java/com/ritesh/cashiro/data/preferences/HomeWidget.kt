package com.ritesh.cashiro.data.preferences

enum class HomeWidget(val displayName: String, val defaultOrder: Int) {
    NETWORTH_SUMMARY("Net Worth", 0),
    LOANS("Loans", 1),
    ACCOUNT_CAROUSEL("Accounts", 2),
    UPCOMING_SUBSCRIPTIONS("Upcoming Subscriptions", 3),
    RECENT_TRANSACTIONS("Recent Transactions", 4),
    BUDGET_CAROUSEL("Budgets", 5),
    TRANSACTION_HEATMAP("Activity Heatmap", 6);
    
    companion object {
        fun fromName(name: String): HomeWidget? {
            return entries.find { it.name == name }
        }
    }
}
