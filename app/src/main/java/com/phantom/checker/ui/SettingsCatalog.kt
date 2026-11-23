package com.phantom.checker.ui

import android.content.Context
import com.phantom.checker.service.HeartbeatPolicy
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import com.phantom.checker.R

data class SettingSearchItem(
    val id: String,
    val cardKey: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val aliasesRes: Int,
    val page: String? = null
) {
    val title: String get() = com.phantom.checker.PhantomApplication.str(titleRes)
    val description: String get() = com.phantom.checker.PhantomApplication.str(descriptionRes)
    val aliases: List<String> get() = com.phantom.checker.PhantomApplication.str(aliasesRes).split("|").filter { it.isNotEmpty() }
}

/**
 * 設定項目單一來源（SettingsActivity 搜尋與 ChatScreen 對話調整設定共用）。
 */
object SettingsCatalog {

    val ITEMS: List<SettingSearchItem> = listOf(
        SettingSearchItem("quick_token_saver", "quick", R.string.catalog_quick_token_saver_title, R.string.catalog_quick_token_saver_desc, R.string.catalog_quick_token_saver_aliases),
        SettingSearchItem("quick_persistent_notif", "quick", R.string.catalog_quick_persistent_notif_title, R.string.catalog_quick_persistent_notif_desc, R.string.catalog_quick_persistent_notif_aliases),
        SettingSearchItem("quick_ai_risk", "quick", R.string.catalog_quick_ai_risk_title, R.string.catalog_quick_ai_risk_desc, R.string.catalog_quick_ai_risk_aliases),
        SettingSearchItem("quick_scam_popup", "quick", R.string.catalog_quick_scam_popup_title, R.string.catalog_quick_scam_popup_desc, R.string.catalog_quick_scam_popup_aliases),
        SettingSearchItem("ai_auto_tag", "quick", R.string.catalog_ai_auto_tag_title, R.string.catalog_ai_auto_tag_desc, R.string.catalog_ai_auto_tag_aliases),
        SettingSearchItem("ai_providers", "ai", R.string.catalog_ai_providers_title, R.string.catalog_ai_providers_desc, R.string.catalog_ai_providers_aliases, page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_tag_dispatch_test", "ai", R.string.catalog_ai_tag_dispatch_test_title, R.string.catalog_ai_tag_dispatch_test_desc, R.string.catalog_ai_tag_dispatch_test_aliases),
        SettingSearchItem("ai_chat_suggestions", "ai", R.string.catalog_ai_chat_suggestions_title, R.string.catalog_ai_chat_suggestions_desc, R.string.catalog_ai_chat_suggestions_aliases),
        SettingSearchItem("ai_chat_tab_behavior", "ai", R.string.catalog_ai_chat_tab_behavior_title, R.string.catalog_ai_chat_tab_behavior_desc, R.string.catalog_ai_chat_tab_behavior_aliases),
        SettingSearchItem("ai_quick_commands", "ai", R.string.catalog_ai_quick_commands_title, R.string.catalog_ai_quick_commands_desc, R.string.catalog_ai_quick_commands_aliases),
        SettingSearchItem("ai_daily_limit", "ai", R.string.catalog_ai_daily_limit_title, R.string.catalog_ai_daily_limit_desc, R.string.catalog_ai_daily_limit_aliases),
        SettingSearchItem("ai_chat_rag_limit", "ai", R.string.catalog_ai_chat_rag_limit_title, R.string.catalog_ai_chat_rag_limit_desc, R.string.catalog_ai_chat_rag_limit_aliases),
        SettingSearchItem("ai_auto_reply", "ai", R.string.catalog_ai_auto_reply_title, R.string.catalog_ai_auto_reply_desc, R.string.catalog_ai_auto_reply_aliases, page = "AutoReplySettingsActivity"),
        SettingSearchItem("ai_scam_center", "ai", R.string.catalog_ai_scam_center_title, R.string.catalog_ai_scam_center_desc, R.string.catalog_ai_scam_center_aliases, page = "ScamCenterActivity"),
        SettingSearchItem("ai_alert_keywords", "ai", R.string.catalog_ai_alert_keywords_title, R.string.catalog_ai_alert_keywords_desc, R.string.catalog_ai_alert_keywords_aliases),
        SettingSearchItem("ai_min_trigger_length", "ai", R.string.catalog_ai_min_trigger_length_title, R.string.catalog_ai_min_trigger_length_desc, R.string.catalog_ai_min_trigger_length_aliases),
        SettingSearchItem("ai_offline_retry_enabled", "ai", R.string.catalog_ai_offline_retry_enabled_title, R.string.catalog_ai_offline_retry_enabled_desc, R.string.catalog_ai_offline_retry_enabled_aliases),
        SettingSearchItem("ai_auto_memo_enabled", "ai", R.string.catalog_ai_auto_memo_enabled_title, R.string.catalog_ai_auto_memo_enabled_desc, R.string.catalog_ai_auto_memo_enabled_aliases),
        SettingSearchItem("ai_tag_failure_notif_enabled", "ai", R.string.catalog_ai_tag_failure_notif_enabled_title, R.string.catalog_ai_tag_failure_notif_enabled_desc, R.string.catalog_ai_tag_failure_notif_enabled_aliases),
        SettingSearchItem("ai_dual_model_routing", "ai", R.string.catalog_ai_dual_model_routing_title, R.string.catalog_ai_dual_model_routing_desc, R.string.catalog_ai_dual_model_routing_aliases),
        SettingSearchItem("ai_dual_model_routing_model", "ai", R.string.catalog_ai_dual_model_routing_model_title, R.string.catalog_ai_dual_model_routing_model_desc, R.string.catalog_ai_dual_model_routing_model_aliases),
        SettingSearchItem("ai_model_chat", "ai", R.string.catalog_ai_model_chat_title, R.string.catalog_ai_model_chat_desc, R.string.catalog_ai_model_chat_aliases, page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_message_analysis", "ai", R.string.catalog_ai_model_message_analysis_title, R.string.catalog_ai_model_message_analysis_desc, R.string.catalog_ai_model_message_analysis_aliases, page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_weekly_summary", "ai", R.string.catalog_ai_model_weekly_summary_title, R.string.catalog_ai_model_weekly_summary_desc, R.string.catalog_ai_model_weekly_summary_aliases, page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_deep_analysis", "ai", R.string.catalog_ai_model_deep_analysis_title, R.string.catalog_ai_model_deep_analysis_desc, R.string.catalog_ai_model_deep_analysis_aliases, page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_settings_search", "ai", R.string.catalog_ai_model_settings_search_title, R.string.catalog_ai_model_settings_search_desc, R.string.catalog_ai_model_settings_search_aliases, page = "ApiKeySettingsActivity"),
        SettingSearchItem("notif_style", "notif", R.string.catalog_notif_style_title, R.string.catalog_notif_style_desc, R.string.catalog_notif_style_aliases),
        SettingSearchItem("notif_persistent_icons", "notif", R.string.catalog_notif_persistent_icons_title, R.string.catalog_notif_persistent_icons_desc, R.string.catalog_notif_persistent_icons_aliases),
        SettingSearchItem("notif_ongoing", "notif", R.string.catalog_notif_ongoing_title, R.string.catalog_notif_ongoing_desc, R.string.catalog_notif_ongoing_aliases),
        SettingSearchItem("notif_quiet_hours", "notif", R.string.catalog_notif_quiet_hours_title, R.string.catalog_notif_quiet_hours_desc, R.string.catalog_notif_quiet_hours_aliases),
        SettingSearchItem("notif_filter_rules", "notif", R.string.catalog_notif_filter_rules_title, R.string.catalog_notif_filter_rules_desc, R.string.catalog_notif_filter_rules_aliases),
        SettingSearchItem("notif_interaction", "notif", R.string.catalog_notif_interaction_title, R.string.catalog_notif_interaction_desc, R.string.catalog_notif_interaction_aliases),
        SettingSearchItem("notif_swipe_delete", "notif", R.string.catalog_notif_swipe_delete_title, R.string.catalog_notif_swipe_delete_desc, R.string.catalog_notif_swipe_delete_aliases),
        SettingSearchItem("notif_throttle", "notif", R.string.catalog_notif_throttle_title, R.string.catalog_notif_throttle_desc, R.string.catalog_notif_throttle_aliases),
        SettingSearchItem("notif_digit_variance", "notif", R.string.catalog_notif_digit_variance_title, R.string.catalog_notif_digit_variance_desc, R.string.catalog_notif_digit_variance_aliases),
        SettingSearchItem("notif_reply_proxy", "notif", R.string.catalog_notif_reply_proxy_title, R.string.catalog_notif_reply_proxy_desc, R.string.catalog_notif_reply_proxy_aliases),
        SettingSearchItem("notif_reply_proxy_timeout", "notif", R.string.catalog_notif_reply_proxy_timeout_title, R.string.catalog_notif_reply_proxy_timeout_desc, R.string.catalog_notif_reply_proxy_timeout_aliases),
        SettingSearchItem("notif_my_display_names", "notif", R.string.catalog_notif_my_display_names_title, R.string.catalog_notif_my_display_names_desc, R.string.catalog_notif_my_display_names_aliases),
        SettingSearchItem("notif_clear_read", "notif", R.string.catalog_notif_clear_read_title, R.string.catalog_notif_clear_read_desc, R.string.catalog_notif_clear_read_aliases),
        SettingSearchItem("data_retention", "data", R.string.catalog_data_retention_title, R.string.catalog_data_retention_desc, R.string.catalog_data_retention_aliases),
        SettingSearchItem("data_muted_apps", "data", R.string.catalog_data_muted_apps_title, R.string.catalog_data_muted_apps_desc, R.string.catalog_data_muted_apps_aliases),
        SettingSearchItem("ai_rules", "data", R.string.catalog_ai_rules_title, R.string.catalog_ai_rules_desc, R.string.catalog_ai_rules_aliases, page = "TagManagementActivity"),
        SettingSearchItem("whitelist_management", "data", R.string.catalog_whitelist_management_title, R.string.catalog_whitelist_management_desc, R.string.catalog_whitelist_management_aliases, page = "WhitelistManagementActivity"),
        SettingSearchItem("data_export_clear", "data", R.string.catalog_data_export_clear_title, R.string.catalog_data_export_clear_desc, R.string.catalog_data_export_clear_aliases),
        SettingSearchItem("appearance_widget_tour", "data", R.string.catalog_appearance_widget_tour_title, R.string.catalog_appearance_widget_tour_desc, R.string.catalog_appearance_widget_tour_aliases),
        SettingSearchItem("appearance_theme", "appearance", R.string.catalog_appearance_theme_title, R.string.catalog_appearance_theme_desc, R.string.catalog_appearance_theme_aliases),
        SettingSearchItem("appearance_language", "appearance", R.string.catalog_appearance_language_title, R.string.catalog_appearance_language_desc, R.string.catalog_appearance_language_aliases),
        SettingSearchItem("appearance_glass_effect", "appearance", R.string.catalog_appearance_glass_effect_title, R.string.catalog_appearance_glass_effect_desc, R.string.catalog_appearance_glass_effect_aliases),
        SettingSearchItem("appearance_tab_bar_mode", "appearance", R.string.catalog_appearance_tab_bar_mode_title, R.string.catalog_appearance_tab_bar_mode_desc, R.string.catalog_appearance_tab_bar_mode_aliases),
        SettingSearchItem("appearance_haptic", "appearance", R.string.catalog_appearance_haptic_title, R.string.catalog_appearance_haptic_desc, R.string.catalog_appearance_haptic_aliases),
        SettingSearchItem("appearance_keepalive", "appearance", R.string.catalog_appearance_keepalive_title, R.string.catalog_appearance_keepalive_desc, R.string.catalog_appearance_keepalive_aliases),
        SettingSearchItem("intel_enabled", "intel", R.string.catalog_intel_enabled_title, R.string.catalog_intel_enabled_desc, R.string.catalog_intel_enabled_aliases),
        SettingSearchItem("intel_sources", "intel", R.string.catalog_intel_sources_title, R.string.catalog_intel_sources_desc, R.string.catalog_intel_sources_aliases),
        SettingSearchItem("shizuku_mode", "shizuku", R.string.catalog_shizuku_mode_title, R.string.catalog_shizuku_mode_desc, R.string.catalog_shizuku_mode_aliases),
        SettingSearchItem("personal_density", "personalization", R.string.catalog_personal_density_title, R.string.catalog_personal_density_desc, R.string.catalog_personal_density_aliases),
        SettingSearchItem("personal_sort", "personalization", R.string.catalog_personal_sort_title, R.string.catalog_personal_sort_desc, R.string.catalog_personal_sort_aliases),
        SettingSearchItem("personal_filters", "personalization", R.string.catalog_personal_filters_title, R.string.catalog_personal_filters_desc, R.string.catalog_personal_filters_aliases),
        SettingSearchItem("personal_summary_collapse", "personalization", R.string.catalog_personal_summary_collapse_title, R.string.catalog_personal_summary_collapse_desc, R.string.catalog_personal_summary_collapse_aliases),
        SettingSearchItem("personal_home_card_layout", "personalization", R.string.catalog_personal_home_card_layout_title, R.string.catalog_personal_home_card_layout_desc, R.string.catalog_personal_home_card_layout_aliases),
        SettingSearchItem("personal_notes_card_layout", "personalization", R.string.catalog_personal_notes_card_layout_title, R.string.catalog_personal_notes_card_layout_desc, R.string.catalog_personal_notes_card_layout_aliases),
        SettingSearchItem("personal_notes_todo_collapsed", "personalization", R.string.catalog_personal_notes_todo_collapsed_title, R.string.catalog_personal_notes_todo_collapsed_desc, R.string.catalog_personal_notes_todo_collapsed_aliases),
        SettingSearchItem("personal_home_filter_chips", "personalization", R.string.catalog_personal_home_filter_chips_title, R.string.catalog_personal_home_filter_chips_desc, R.string.catalog_personal_home_filter_chips_aliases),
        SettingSearchItem("personal_time_format", "personalization", R.string.catalog_personal_time_format_title, R.string.catalog_personal_time_format_desc, R.string.catalog_personal_time_format_aliases),
        SettingSearchItem("bg_global", "personalization", R.string.catalog_bg_global_title, R.string.catalog_bg_global_desc, R.string.catalog_bg_global_aliases),
        SettingSearchItem("bg_chat", "personalization", R.string.catalog_bg_chat_title, R.string.catalog_bg_chat_desc, R.string.catalog_bg_chat_aliases),
        SettingSearchItem("stats_card_visibility", "stats", R.string.catalog_stats_card_visibility_title, R.string.catalog_stats_card_visibility_desc, R.string.catalog_stats_card_visibility_aliases),
        SettingSearchItem("stats_briefing", "stats", R.string.catalog_stats_briefing_title, R.string.catalog_stats_briefing_desc, R.string.catalog_stats_briefing_aliases),
        SettingSearchItem("stats_weekly_bar", "stats", R.string.catalog_stats_weekly_bar_title, R.string.catalog_stats_weekly_bar_desc, R.string.catalog_stats_weekly_bar_aliases),
        SettingSearchItem("stats_risk_dist", "stats", R.string.catalog_stats_risk_dist_title, R.string.catalog_stats_risk_dist_desc, R.string.catalog_stats_risk_dist_aliases),
        SettingSearchItem("stats_hourly", "stats", R.string.catalog_stats_hourly_title, R.string.catalog_stats_hourly_desc, R.string.catalog_stats_hourly_aliases),
        SettingSearchItem("stats_daily_top_apps", "stats", R.string.catalog_stats_daily_top_apps_title, R.string.catalog_stats_daily_top_apps_desc, R.string.catalog_stats_daily_top_apps_aliases),
        SettingSearchItem("stats_ai_tags", "stats", R.string.catalog_stats_ai_tags_title, R.string.catalog_stats_ai_tags_desc, R.string.catalog_stats_ai_tags_aliases),
        SettingSearchItem("stats_harassment", "stats", R.string.catalog_stats_harassment_title, R.string.catalog_stats_harassment_desc, R.string.catalog_stats_harassment_aliases),
        SettingSearchItem("stats_top_risky", "stats", R.string.catalog_stats_top_risky_title, R.string.catalog_stats_top_risky_desc, R.string.catalog_stats_top_risky_aliases),
        SettingSearchItem("stats_risk_trend", "stats", R.string.catalog_stats_risk_trend_title, R.string.catalog_stats_risk_trend_desc, R.string.catalog_stats_risk_trend_aliases),
        SettingSearchItem("stats_unread_ratio", "stats", R.string.catalog_stats_unread_ratio_title, R.string.catalog_stats_unread_ratio_desc, R.string.catalog_stats_unread_ratio_aliases),
        SettingSearchItem("stats_app_share", "stats", R.string.catalog_stats_app_share_title, R.string.catalog_stats_app_share_desc, R.string.catalog_stats_app_share_aliases),
        SettingSearchItem("stats_todo_done", "stats", R.string.catalog_stats_todo_done_title, R.string.catalog_stats_todo_done_desc, R.string.catalog_stats_todo_done_aliases),
        SettingSearchItem("bg_conversation", "personalization", R.string.catalog_bg_conversation_title, R.string.catalog_bg_conversation_desc, R.string.catalog_bg_conversation_aliases),
        SettingSearchItem("bg_scrim", "personalization", R.string.catalog_bg_scrim_title, R.string.catalog_bg_scrim_desc, R.string.catalog_bg_scrim_aliases),
        SettingSearchItem("glass_strength", "personalization", R.string.catalog_glass_strength_title, R.string.catalog_glass_strength_desc, R.string.catalog_glass_strength_aliases),
        SettingSearchItem("appearance_card_bg", "appearance", R.string.catalog_appearance_card_bg_title, R.string.catalog_appearance_card_bg_desc, R.string.catalog_appearance_card_bg_aliases),
        SettingSearchItem("assistant_header_background", "personalization", R.string.catalog_assistant_header_background_title, R.string.catalog_assistant_header_background_desc, R.string.catalog_assistant_header_background_aliases),
        SettingSearchItem("chat_font_scale", "personalization", R.string.catalog_chat_font_scale_title, R.string.catalog_chat_font_scale_desc, R.string.catalog_chat_font_scale_aliases),
        SettingSearchItem("messages_font_scale", "personalization", R.string.catalog_messages_font_scale_title, R.string.catalog_messages_font_scale_desc, R.string.catalog_messages_font_scale_aliases),
        SettingSearchItem("chat_bubble_corner_dp", "personalization", R.string.catalog_chat_bubble_corner_dp_title, R.string.catalog_chat_bubble_corner_dp_desc, R.string.catalog_chat_bubble_corner_dp_aliases),
        SettingSearchItem("chat_bubble_width_ratio", "personalization", R.string.catalog_chat_bubble_width_ratio_title, R.string.catalog_chat_bubble_width_ratio_desc, R.string.catalog_chat_bubble_width_ratio_aliases),
        SettingSearchItem("favorite_apps_max_visible", "personalization", R.string.catalog_favorite_apps_max_visible_title, R.string.catalog_favorite_apps_max_visible_desc, R.string.catalog_favorite_apps_max_visible_aliases),
        SettingSearchItem("memory_chat_capture", "memory_assistant", R.string.catalog_memory_chat_capture_title, R.string.catalog_memory_chat_capture_desc, R.string.catalog_memory_chat_capture_aliases),
        SettingSearchItem("memory_write_confirm", "memory_assistant", R.string.catalog_memory_write_confirm_title, R.string.catalog_memory_write_confirm_desc, R.string.catalog_memory_write_confirm_aliases),
        SettingSearchItem("memory_normal_top_k", "memory_assistant", R.string.catalog_memory_normal_top_k_title, R.string.catalog_memory_normal_top_k_desc, R.string.catalog_memory_normal_top_k_aliases),
        SettingSearchItem("memory_normal_per_item_chars", "memory_assistant", R.string.catalog_memory_normal_per_item_chars_title, R.string.catalog_memory_normal_per_item_chars_desc, R.string.catalog_memory_normal_per_item_chars_aliases),
        SettingSearchItem("memory_saver_top_k", "memory_assistant", R.string.catalog_memory_saver_top_k_title, R.string.catalog_memory_saver_top_k_desc, R.string.catalog_memory_saver_top_k_aliases),
        SettingSearchItem("assistant_custom_name", "memory_assistant", R.string.catalog_assistant_custom_name_title, R.string.catalog_assistant_custom_name_desc, R.string.catalog_assistant_custom_name_aliases),
        SettingSearchItem("assistant_personality", "memory_assistant", R.string.catalog_assistant_personality_title, R.string.catalog_assistant_personality_desc, R.string.catalog_assistant_personality_aliases),
        SettingSearchItem("assistant_verbosity", "memory_assistant", R.string.catalog_assistant_verbosity_title, R.string.catalog_assistant_verbosity_desc, R.string.catalog_assistant_verbosity_aliases),
        SettingSearchItem("assistant_avatar", "memory_assistant", R.string.catalog_assistant_avatar_title, R.string.catalog_assistant_avatar_desc, R.string.catalog_assistant_avatar_aliases),
        SettingSearchItem("memory_privacy_tier_info", "memory_assistant", R.string.catalog_memory_privacy_tier_info_title, R.string.catalog_memory_privacy_tier_info_desc, R.string.catalog_memory_privacy_tier_info_aliases),
        SettingSearchItem("ai_anti_fraud_cross_check", "memory_assistant", R.string.catalog_ai_anti_fraud_cross_check_title, R.string.catalog_ai_anti_fraud_cross_check_desc, R.string.catalog_ai_anti_fraud_cross_check_aliases),
        SettingSearchItem("calendar_integration", "memory_assistant", R.string.catalog_calendar_integration_title, R.string.catalog_calendar_integration_desc, R.string.catalog_calendar_integration_aliases),
        SettingSearchItem("calendar_write_confirm", "memory_assistant", R.string.catalog_calendar_write_confirm_title, R.string.catalog_calendar_write_confirm_desc, R.string.catalog_calendar_write_confirm_aliases),
        SettingSearchItem("schedule_memo_route", "memory_assistant", R.string.catalog_schedule_memo_route_title, R.string.catalog_schedule_memo_route_desc, R.string.catalog_schedule_memo_route_aliases),
        SettingSearchItem("permission_hub", "hub", R.string.search_s_1579f9, R.string.catalog_permission_hub_desc, R.string.catalog_permission_hub_aliases, page = "PermissionHubActivity"),
        SettingSearchItem("assistant_hub", "hub", R.string.catalog_assistant_hub_title, R.string.catalog_assistant_hub_desc, R.string.catalog_assistant_hub_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("desktop_link", "hub", R.string.catalog_desktop_link_title, R.string.catalog_desktop_link_desc, R.string.catalog_desktop_link_aliases, page = "LinkHubActivity"),
        SettingSearchItem("contacts_integration", "hub", R.string.catalog_contacts_integration_title, R.string.catalog_contacts_integration_desc, R.string.catalog_contacts_integration_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("briefing_morning_enabled", "hub", R.string.catalog_briefing_morning_enabled_title, R.string.catalog_briefing_morning_enabled_desc, R.string.catalog_briefing_morning_enabled_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("briefing_evening_enabled", "hub", R.string.catalog_briefing_evening_enabled_title, R.string.catalog_briefing_evening_enabled_desc, R.string.catalog_briefing_evening_enabled_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("proactive_daily_cap", "hub", R.string.catalog_proactive_daily_cap_title, R.string.catalog_proactive_daily_cap_desc, R.string.catalog_proactive_daily_cap_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("briefing_daily_cap", "hub", R.string.catalog_briefing_daily_cap_title, R.string.catalog_briefing_daily_cap_desc, R.string.catalog_briefing_daily_cap_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("proactive_reminder_lead_minutes", "hub", R.string.catalog_proactive_reminder_lead_minutes_title, R.string.catalog_proactive_reminder_lead_minutes_desc, R.string.catalog_proactive_reminder_lead_minutes_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("proactive_allday_remind_time", "hub", R.string.catalog_proactive_allday_remind_time_title, R.string.catalog_proactive_allday_remind_time_desc, R.string.catalog_proactive_allday_remind_time_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("proactive_monitor_interval_minutes", "hub", R.string.catalog_proactive_monitor_interval_minutes_title, R.string.catalog_proactive_monitor_interval_minutes_desc, R.string.catalog_proactive_monitor_interval_minutes_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("proactive_dedup_window_minutes", "hub", R.string.catalog_proactive_dedup_window_minutes_title, R.string.catalog_proactive_dedup_window_minutes_desc, R.string.catalog_proactive_dedup_window_minutes_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("proactive_dnd_enabled", "hub", R.string.catalog_proactive_dnd_enabled_title, R.string.catalog_proactive_dnd_enabled_desc, R.string.catalog_proactive_dnd_enabled_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("proactive_calendar_insight_enabled", "hub", R.string.catalog_proactive_calendar_insight_enabled_title, R.string.catalog_proactive_calendar_insight_enabled_desc, R.string.catalog_proactive_calendar_insight_enabled_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("proactive_calendar_insight_notif_enabled", "hub", R.string.catalog_proactive_calendar_insight_notif_enabled_title, R.string.catalog_proactive_calendar_insight_notif_enabled_desc, R.string.catalog_proactive_calendar_insight_notif_enabled_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("proactive_calendar_web_search_enabled", "hub", R.string.catalog_proactive_calendar_web_search_enabled_title, R.string.catalog_proactive_calendar_web_search_enabled_desc, R.string.catalog_proactive_calendar_web_search_enabled_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("calendar_insight_interval_minutes", "hub", R.string.catalog_calendar_insight_interval_minutes_title, R.string.catalog_calendar_insight_interval_minutes_desc, R.string.catalog_calendar_insight_interval_minutes_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("memory_auto_extract_enabled", "hub", R.string.catalog_memory_auto_extract_enabled_title, R.string.catalog_memory_auto_extract_enabled_desc, R.string.catalog_memory_auto_extract_enabled_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("chat_pending_capsules_enabled", "hub", R.string.catalog_chat_pending_capsules_enabled_title, R.string.catalog_chat_pending_capsules_enabled_desc, R.string.catalog_chat_pending_capsules_enabled_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("chat_bubble_timestamp_enabled", "about", R.string.catalog_chat_bubble_timestamp_enabled_title, R.string.catalog_chat_bubble_timestamp_enabled_desc, R.string.catalog_chat_bubble_timestamp_enabled_aliases),
        SettingSearchItem("diary_daily_enabled", "hub", R.string.catalog_diary_daily_enabled_title, R.string.catalog_diary_daily_enabled_desc, R.string.catalog_diary_daily_enabled_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("diary_annotation_in_saver", "hub", R.string.catalog_diary_annotation_in_saver_title, R.string.catalog_diary_annotation_in_saver_desc, R.string.catalog_diary_annotation_in_saver_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("goal_tracking_enabled", "hub", R.string.catalog_goal_tracking_enabled_title, R.string.catalog_goal_tracking_enabled_desc, R.string.catalog_goal_tracking_enabled_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("health_briefing_enabled", "hub", R.string.catalog_health_briefing_enabled_title, R.string.catalog_health_briefing_enabled_desc, R.string.catalog_health_briefing_enabled_aliases, page = "PermissionHubActivity"),
        SettingSearchItem("health_soft_invite_enabled", "hub", R.string.catalog_health_soft_invite_enabled_title, R.string.catalog_health_soft_invite_enabled_desc, R.string.catalog_health_soft_invite_enabled_aliases, page = "PermissionHubActivity"),
        SettingSearchItem("health_ai_query_enabled", "hub", R.string.catalog_health_ai_query_enabled_title, R.string.catalog_health_ai_query_enabled_desc, R.string.catalog_health_ai_query_enabled_aliases, page = "PermissionHubActivity"),
        SettingSearchItem("decision_confirm_threshold", "hub", R.string.catalog_decision_confirm_threshold_title, R.string.catalog_decision_confirm_threshold_desc, R.string.catalog_decision_confirm_threshold_aliases, page = "AssistantHubActivity"),
        SettingSearchItem("ai_model_briefing", "ai", R.string.catalog_ai_model_briefing_title, R.string.catalog_ai_model_briefing_desc, R.string.catalog_ai_model_briefing_aliases, page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_diary_annotation", "ai", R.string.catalog_ai_model_diary_annotation_title, R.string.catalog_ai_model_diary_annotation_desc, R.string.catalog_ai_model_diary_annotation_aliases, page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_proactive", "ai", R.string.catalog_ai_model_proactive_title, R.string.catalog_ai_model_proactive_desc, R.string.catalog_ai_model_proactive_aliases, page = "ApiKeySettingsActivity"),
        SettingSearchItem("proactive_intel_enabled", "intel", R.string.catalog_proactive_intel_enabled_title, R.string.catalog_proactive_intel_enabled_desc, R.string.catalog_proactive_intel_enabled_aliases),
        SettingSearchItem("proactive_extract_enabled", "intel", R.string.catalog_proactive_extract_enabled_title, R.string.catalog_proactive_extract_enabled_desc, R.string.catalog_proactive_extract_enabled_aliases),
        SettingSearchItem("proactive_intel_freq_hours", "intel", R.string.catalog_proactive_intel_freq_hours_title, R.string.catalog_proactive_intel_freq_hours_desc, R.string.catalog_proactive_intel_freq_hours_aliases),
        SettingSearchItem("proactive_watch_topics", "intel", R.string.catalog_proactive_watch_topics_title, R.string.catalog_proactive_watch_topics_desc, R.string.catalog_proactive_watch_topics_aliases),
        SettingSearchItem("linked_apps_center", "hub", R.string.catalog_linked_apps_center_title, R.string.catalog_linked_apps_center_desc, R.string.catalog_linked_apps_center_aliases, page = "LinkedAppsActivity"),
        SettingSearchItem("update_auto_check", "about", R.string.catalog_update_auto_check_title, R.string.catalog_update_auto_check_desc, R.string.catalog_update_auto_check_aliases),
        SettingSearchItem("update_snooze_minutes", "about", R.string.catalog_update_snooze_minutes_title, R.string.catalog_update_snooze_minutes_desc, R.string.catalog_update_snooze_minutes_aliases),
        SettingSearchItem("about_section", "about", R.string.catalog_about_section_title, R.string.catalog_about_section_desc, R.string.catalog_about_section_aliases)
    )

    /** 僅允許 boolean 型開關透過 AI 對話直接切換，必須與 ITEMS 一致 */
    val TOGGLEABLE_IDS: Set<String> = setOf(
        "quick_token_saver",
        "quick_persistent_notif",
        "quick_ai_risk",
        "quick_scam_popup",
        "ai_auto_tag",
        "ai_offline_retry_enabled",
        "ai_auto_memo_enabled",
        "ai_tag_failure_notif_enabled",
        "ai_dual_model_routing",
        "ai_chat_suggestions",
        "ai_chat_tab_behavior",
        "memory_chat_capture",
        "memory_write_confirm",
        "calendar_write_confirm",
        "ai_anti_fraud_cross_check",
        "briefing_morning_enabled",
        "briefing_evening_enabled",
        "proactive_dnd_enabled",
        "proactive_calendar_insight_enabled",
        "proactive_calendar_insight_notif_enabled",
        "proactive_calendar_web_search_enabled",
        "memory_auto_extract_enabled",
        "chat_pending_capsules_enabled",
        "notif_persistent_icons",
        "notif_ongoing",
        "notif_quiet_hours",
        "notif_swipe_delete",
        "notif_digit_variance",
        "notif_reply_proxy",
        "appearance_glass_effect",
        "appearance_haptic",
        "intel_enabled",
        "personal_summary_collapse",
        "stats_briefing",
        "stats_weekly_bar",
        "stats_risk_dist",
        "stats_hourly",
        "stats_daily_top_apps",
        "stats_ai_tags",
        "stats_harassment",
        "stats_top_risky",
        "stats_risk_trend",
        "stats_unread_ratio",
        "stats_app_share",
        "stats_todo_done",
        "chat_bubble_timestamp_enabled",
        "diary_daily_enabled",
        "diary_annotation_in_saver",
        "goal_tracking_enabled",
        "health_briefing_enabled",
        "health_soft_invite_enabled",
        "health_ai_query_enabled",
        "proactive_intel_enabled",
        "proactive_extract_enabled",
        "update_auto_check"
    )

    fun findById(id: String): SettingSearchItem? = ITEMS.firstOrNull { it.id == id }

    fun isToggleable(id: String): Boolean = id in TOGGLEABLE_IDS && findById(id) != null

    fun getBooleanState(ctx: Context, id: String): Boolean? {
        val appCtx = ctx.applicationContext
        return when (id) {
            "quick_token_saver" -> PhantomPrefs.isTokenSaverEnabled(appCtx)
            "quick_persistent_notif" -> PhantomPrefs.isPersistentStatsEnabled(appCtx)
            "quick_ai_risk" -> PhantomPrefs.isAiRiskEnabled(appCtx)
            "quick_scam_popup" -> PhantomPrefs.isScamAlertNotifEnabled(appCtx)
            "ai_auto_tag" -> PhantomPrefs.isAiAutoTagEnabled(appCtx)
            "ai_offline_retry_enabled" -> PhantomPrefs.isAiOfflineRetryEnabled(appCtx)
            "ai_auto_memo_enabled" -> PhantomPrefs.isAiAutoMemoEnabled(appCtx)
            "ai_tag_failure_notif_enabled" -> PhantomPrefs.isAiTagFailureNotifEnabled(appCtx)
            "ai_dual_model_routing" -> PhantomPrefs.isDualModelRoutingEnabled(appCtx)
            "ai_chat_suggestions" -> PhantomPrefs.isChatSuggestionsEnabled(appCtx)
            "ai_chat_tab_behavior" -> PhantomPrefs.isAiChatTabContinue(appCtx)
            "memory_chat_capture" -> PhantomPrefs.isMemoryChatCaptureEnabled(appCtx)
            "memory_write_confirm" -> PhantomPrefs.isMemoryWriteConfirmEnabled(appCtx)
            "calendar_write_confirm" -> PhantomPrefs.isCalendarWriteConfirmEnabled(appCtx)
            "ai_anti_fraud_cross_check" -> PhantomPrefs.isAntiFraudCrossCheckEnabled(appCtx)
            "briefing_morning_enabled" -> PhantomPrefs.isMorningBriefingEnabled(appCtx)
            "briefing_evening_enabled" -> PhantomPrefs.isEveningBriefingEnabled(appCtx)
            "proactive_dnd_enabled" -> PhantomPrefs.isProactiveDndEnabled(appCtx)
            "proactive_calendar_insight_enabled" -> PhantomPrefs.isProactiveCalendarInsightEnabled(appCtx)
            "proactive_calendar_insight_notif_enabled" -> PhantomPrefs.isProactiveCalendarInsightNotifEnabled(appCtx)
            "proactive_calendar_web_search_enabled" -> PhantomPrefs.isProactiveCalendarWebSearchEnabled(appCtx)
            "memory_auto_extract_enabled" -> PhantomPrefs.isMemoryAutoExtractEnabled(appCtx)
            "chat_pending_capsules_enabled" -> PhantomPrefs.isChatPendingCapsulesEnabled(appCtx)
            "notif_persistent_icons" -> PhantomPrefs.isPersistentNotifShowIcons(appCtx)
            "notif_ongoing" -> PhantomPrefs.isRecordOngoingEnabled(appCtx)
            "notif_quiet_hours" -> PhantomPrefs.isQuietHoursEnabled(appCtx)
            "notif_swipe_delete" -> PhantomPrefs.isSwipeToDeleteEnabled(appCtx)
            "notif_digit_variance" -> PhantomPrefs.isSameKeyDigitVarianceThrottleEnabled(appCtx)
            "notif_reply_proxy" -> PhantomPrefs.isReplyProxyEnabled(appCtx)
            "appearance_glass_effect" -> ThemePreferences.isGlassEffectEnabled(appCtx)
            "appearance_haptic" -> PhantomPrefs.isHapticFeedbackEnabled(appCtx)
            "intel_enabled" -> PhantomPrefs.isIntelEnabled(appCtx)
            "personal_summary_collapse" -> PhantomPrefs.isSummaryDefaultCollapsed(appCtx)
            "stats_briefing",
            "stats_weekly_bar",
            "stats_risk_dist",
            "stats_hourly",
            "stats_daily_top_apps",
            "stats_ai_tags",
            "stats_harassment",
            "stats_top_risky",
            "stats_risk_trend",
            "stats_unread_ratio",
            "stats_app_share",
            "stats_todo_done" -> PhantomPrefs.isStatsCardVisible(appCtx, id)
            "chat_bubble_timestamp_enabled" -> PhantomPrefs.isChatTimestampEnabled(appCtx)
            "diary_daily_enabled" -> PhantomPrefs.isDiaryDailyEnabled(appCtx)
            "diary_annotation_in_saver" -> PhantomPrefs.isDiaryAnnotationInSaverEnabled(appCtx)
            "goal_tracking_enabled" -> PhantomPrefs.isGoalTrackingEnabled(appCtx)
            "health_briefing_enabled" -> PhantomPrefs.isHealthBriefingEnabled(appCtx)
            "health_soft_invite_enabled" -> PhantomPrefs.isHealthSoftInviteEnabled(appCtx)
            "health_ai_query_enabled" -> PhantomPrefs.isHealthAiQueryEnabled(appCtx)
            "proactive_intel_enabled" -> PhantomPrefs.isProactiveIntelEnabled(appCtx)
            "proactive_extract_enabled" -> PhantomPrefs.isProactiveExtractEnabled(appCtx)
            "update_auto_check" -> PhantomPrefs.isUpdateAutoCheckEnabled(appCtx)
            else -> null
        }
    }

    /**
     * 套用 boolean 設定（走與 SettingsActivity 完全相同的 PhantomPrefs setter＋既有副作用）。
     * 回傳 Pair(beforeState, afterState)，若 id 不在可開關白名單則回傳 null。
     */
    fun applyBooleanSetting(ctx: Context, id: String, enabled: Boolean): Pair<Boolean, Boolean>? {
        if (!isToggleable(id)) return null
        val appCtx = ctx.applicationContext
        val before = getBooleanState(appCtx, id) ?: return null
        when (id) {
            "quick_token_saver" -> PhantomPrefs.setTokenSaverEnabled(appCtx, enabled)
            "quick_persistent_notif" -> {
                PhantomPrefs.setPersistentStatsEnabled(appCtx, enabled)
                if (enabled) {
                    HeartbeatPolicy.schedule(appCtx)
                    StatsForegroundService.startOrRefresh(appCtx)
                } else {
                    StatsForegroundService.stop(appCtx)
                }
            }
            "quick_ai_risk" -> PhantomPrefs.setAiRiskEnabled(appCtx, enabled)
            "quick_scam_popup" -> PhantomPrefs.setScamAlertNotifEnabled(appCtx, enabled)
            "ai_auto_tag" -> PhantomPrefs.setAiAutoTagEnabled(appCtx, enabled)
            "ai_offline_retry_enabled" -> {
                PhantomPrefs.setAiOfflineRetryEnabled(appCtx, enabled)
                if (!enabled) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        com.phantom.checker.service.PendingAiAnalysisPolicy.disableAndClear(appCtx)
                    }
                }
            }
            "ai_auto_memo_enabled" -> PhantomPrefs.setAiAutoMemoEnabled(appCtx, enabled)
            "ai_tag_failure_notif_enabled" -> PhantomPrefs.setAiTagFailureNotifEnabled(appCtx, enabled)
            "ai_dual_model_routing" -> PhantomPrefs.setDualModelRoutingEnabled(appCtx, enabled)
            "ai_chat_suggestions" -> PhantomPrefs.setChatSuggestionsEnabled(appCtx, enabled)
            "ai_chat_tab_behavior" -> PhantomPrefs.setAiChatTabBehavior(
                appCtx,
                if (enabled) PhantomPrefs.AI_CHAT_TAB_CONTINUE else PhantomPrefs.AI_CHAT_TAB_NEW
            )
            "memory_chat_capture" -> PhantomPrefs.setMemoryChatCaptureEnabled(appCtx, enabled)
            "memory_write_confirm" -> PhantomPrefs.setMemoryWriteConfirmEnabled(appCtx, enabled)
            "calendar_write_confirm" -> PhantomPrefs.setCalendarWriteConfirmEnabled(appCtx, enabled)
            "ai_anti_fraud_cross_check" -> PhantomPrefs.setAntiFraudCrossCheckEnabled(appCtx, enabled)
            "briefing_morning_enabled" -> {
                PhantomPrefs.setMorningBriefingEnabled(appCtx, enabled)
                com.phantom.checker.service.BriefingScheduler.rescheduleAll(appCtx)
            }
            "briefing_evening_enabled" -> {
                PhantomPrefs.setEveningBriefingEnabled(appCtx, enabled)
                com.phantom.checker.service.BriefingScheduler.rescheduleAll(appCtx)
            }
            "proactive_dnd_enabled" -> PhantomPrefs.setProactiveDndEnabled(appCtx, enabled)
            "proactive_calendar_insight_enabled" -> {
                PhantomPrefs.setProactiveCalendarInsightEnabled(appCtx, enabled)
                com.phantom.checker.service.CalendarInsightScheduler.scheduleOrUpdate(appCtx)
            }
            "proactive_calendar_insight_notif_enabled" -> PhantomPrefs.setProactiveCalendarInsightNotifEnabled(appCtx, enabled)
            "proactive_calendar_web_search_enabled" -> PhantomPrefs.setProactiveCalendarWebSearchEnabled(appCtx, enabled)
            "memory_auto_extract_enabled" -> PhantomPrefs.setMemoryAutoExtractEnabled(appCtx, enabled)
            "chat_pending_capsules_enabled" -> PhantomPrefs.setChatPendingCapsulesEnabled(appCtx, enabled)
            "notif_persistent_icons" -> {
                PhantomPrefs.setPersistentNotifShowIcons(appCtx, enabled)
                StatsForegroundService.startOrRefresh(appCtx, force = true)
            }
            "notif_ongoing" -> PhantomPrefs.setRecordOngoingEnabled(appCtx, enabled)
            "notif_quiet_hours" -> PhantomPrefs.setQuietHoursEnabled(appCtx, enabled)
            "notif_swipe_delete" -> PhantomPrefs.setSwipeToDeleteEnabled(appCtx, enabled)
            "notif_digit_variance" -> PhantomPrefs.setSameKeyDigitVarianceThrottleEnabled(appCtx, enabled)
            "notif_reply_proxy" -> PhantomPrefs.setReplyProxyEnabled(appCtx, enabled)
            "appearance_glass_effect" -> ThemePreferences.setGlassEffectEnabled(appCtx, enabled)
            "appearance_haptic" -> PhantomPrefs.setHapticFeedbackEnabled(appCtx, enabled)
            "intel_enabled" -> PhantomPrefs.setIntelEnabled(appCtx, enabled)
            "personal_summary_collapse" -> PhantomPrefs.setSummaryDefaultCollapsed(appCtx, enabled)
            "stats_briefing",
            "stats_weekly_bar",
            "stats_risk_dist",
            "stats_hourly",
            "stats_daily_top_apps",
            "stats_ai_tags",
            "stats_harassment",
            "stats_top_risky",
            "stats_risk_trend",
            "stats_unread_ratio",
            "stats_app_share",
            "stats_todo_done" -> PhantomPrefs.setStatsCardVisible(appCtx, id, enabled)
            "chat_bubble_timestamp_enabled" -> PhantomPrefs.setChatTimestampEnabled(appCtx, enabled)
            "diary_daily_enabled" -> {
                PhantomPrefs.setDiaryDailyEnabled(appCtx, enabled)
                com.phantom.checker.service.DiaryPolicy.schedule(appCtx)
            }
            "diary_annotation_in_saver" -> PhantomPrefs.setDiaryAnnotationInSaverEnabled(appCtx, enabled)
            "goal_tracking_enabled" -> PhantomPrefs.setGoalTrackingEnabled(appCtx, enabled)
            "health_briefing_enabled" -> PhantomPrefs.setHealthBriefingEnabled(appCtx, enabled)
            "health_soft_invite_enabled" -> PhantomPrefs.setHealthSoftInviteEnabled(appCtx, enabled)
            "health_ai_query_enabled" -> PhantomPrefs.setHealthAiQueryEnabled(appCtx, enabled)
            "proactive_intel_enabled" -> PhantomPrefs.setProactiveIntelEnabled(appCtx, enabled)
            "proactive_extract_enabled" -> PhantomPrefs.setProactiveExtractEnabled(appCtx, enabled)
            "update_auto_check" -> PhantomPrefs.setUpdateAutoCheckEnabled(appCtx, enabled)
            else -> return null
        }
        PhantomSettingsBus.notifyChanged()
        return before to enabled
    }

    /** 供 SettingsActivity AI 語意搜尋動態產生 catalogText */
    fun buildAiSearchCatalogText(): String =
        ITEMS.joinToString("\n") { item ->
            "${item.id} | ${item.title} | ${item.description} | 別名:${item.aliases.joinToString(",")}"
        }

    private val STOP_WORDS: List<String> = listOf(
        "請問", "幫我", "可以", "如何", "怎麼", "在哪裡", "在哪", "哪裡",
        "設定頁", "設定", "頁面", "分頁", "功能", "調整", "修改", "更改", "變更", "切換",
        "控制", "管理", "選項", "相關", "有關", "想要", "一下", "的", "了", "嗎", "呢", "與", "和", "及"
    )

    /** #406 / #423 同義詞群組：任一詞命中時自動展開同組別名參與比對 */
    private val SYNONYM_GROUPS: List<Set<String>> = listOf(
        setOf("深色", "暗黑", "黑暗", "夜間模式", "深色主題", "淺色", "亮色", "主題", "dark", "light", "theme"),
        setOf("字體", "字級", "字大小", "文字大小", "字太大", "字太小", "縮放", "font"),
        setOf("訊息卡", "卡片", "堆疊", "滾輪", "卡片高度", "卡片太高", "排列", "密度", "間距", "92dp"),
        setOf("通知", "推播", "提醒", "警示", "通知欄", "狀態列"),
        setOf("常用", "最愛", "我的最愛", "釘選", "常用清單", "常用app"),
        setOf("晚上", "晚間", "夜間", "睡前", "晚報"),
        setOf("早上", "早間", "晨間", "早晨", "早報"),
        setOf("簡報", "早晚報", "個人簡報", "briefing"),
        setOf("太吵", "勿擾", "靜音", "半夜", "安靜", "節流"),
        setOf("健康", "睡眠", "睡多久", "步數", "走路", "心率", "心跳", "運動", "距離", "公里", "卡路里", "熱量", "體能", "健身", "鍛鍊", "生理", "health"),
        setOf("下載", "更新", "升級", "新版本", "安裝", "apk", "下載卡住"),
        setOf("郵件", "信箱", "email", "gmail", "信件"),
        setOf("氣泡", "對話框", "聊天框", "圓角", "寬度", "bubble"),
        setOf("桌布", "背景", "底圖", "圖片", "封面", "wallpaper"),
        setOf("待辦", "提醒事項", "任務", "todo"),
        setOf("記事", "備忘錄", "筆記", "memo"),
        setOf("防詐", "詐騙", "可疑", "高風險", "165"),
        setOf("省電", "省錢", "省流", "省token", "額度", "token")
    )

    private data class IndexedCatalogItem(
        val item: SettingSearchItem,
        val titleLower: String,
        val aliasesLower: List<String>,
        val descLower: String,
        val fullHaystack: String
    )

    private val INDEXED_ITEMS: List<IndexedCatalogItem> by lazy {
        ITEMS.map { item ->
            val t = item.title.lowercase(java.util.Locale.ROOT)
            val a = item.aliases.map { it.lowercase(java.util.Locale.ROOT) }
            val d = item.description.lowercase(java.util.Locale.ROOT)
            val h = "$t ${a.joinToString(" ")} $d ${item.id.lowercase(java.util.Locale.ROOT)}"
            IndexedCatalogItem(item, t, a, d, h)
        }
    }

    /**
     * #406 將自然語言／多詞查詢拆解為有效語意 token：
     * - 保留原始空白切分 token（確保單一關鍵字既有行為 100% 不回退）
     * - 對較長中文複合句去除虛詞（的、頁面、設定、調整…）後提取核心詞與 2-gram 子詞
     */
    internal fun extractSearchTokens(rawQuery: String): List<String> {
        val cleaned = rawQuery.trim().lowercase(java.util.Locale.ROOT)
        if (cleaned.isEmpty()) return emptyList()

        val whitespaceTokens = cleaned.split(Regex("[\\s,，、/／|｜]+")).filter { it.isNotEmpty() }
        // 單一短詞（≤ 4 字且不含虛詞）直接回傳，確保單一關鍵字行為零回歸
        if (whitespaceTokens.size == 1 && cleaned.length <= 3 && STOP_WORDS.none { cleaned.contains(it) }) {
            return listOf(cleaned)
        }

        val tokens = LinkedHashSet<String>()
        // 1. 先依虛詞與標點切分核心片段
        var stripped = cleaned
        for (sw in STOP_WORDS) {
            stripped = stripped.replace(sw, " ")
        }
        val coreParts = stripped.split(Regex("[\\s,，、/／|｜？?！!。]+")).filter { it.isNotEmpty() }
        for (part in coreParts) {
            tokens.add(part)
            // 若片段仍偏長（例如「訊息卡高度」或「晚上簡報幾點」），補充已知領域核心詞與 2 字元滑動詞
            if (part.length >= 3) {
                for (group in SYNONYM_GROUPS) {
                    for (term in group) {
                        if (term.length >= 2 && part.contains(term)) {
                            tokens.add(term)
                        }
                    }
                }
            }
            if (part.length in 4..8 && part.all { it.code > 127 }) {
                for (i in 0..part.length - 2) {
                    val bi = part.substring(i, i + 2)
                    if (bi !in STOP_WORDS) {
                        tokens.add(bi)
                    }
                }
            }
        }

        if (tokens.isEmpty()) {
            tokens.addAll(whitespaceTokens)
        }
        return tokens.toList()
    }

    private fun expandSynonyms(token: String): Set<String> {
        val out = LinkedHashSet<String>()
        out.add(token)
        for (group in SYNONYM_GROUPS) {
            if (token in group || group.any { it.length >= 2 && (token.contains(it) || it.contains(token)) }) {
                out.addAll(group)
            }
        }
        return out
    }

    /**
     * #406 設定關鍵字搜尋（逐詞命中評分＋虛詞去除＋同義詞展開）：
     * - 完整句直接子字串命中給最高分（確保單一關鍵字 100% 不回歸）
     * - 分詞後每個 token 比對 title（權重高）、aliases（權重中高）、description（權重中），同義詞命中給次高權重
     * - 全量 136 條本機記憶體索引評分耗時 < 1ms
     */
    fun searchByKeywords(rawQuery: String): List<SettingSearchItem> {
        val cleaned = rawQuery.trim().lowercase(java.util.Locale.ROOT)
        if (cleaned.isEmpty()) return emptyList()

        val tokens = extractSearchTokens(cleaned)
        val expandedPerToken: List<Pair<String, Set<String>>> = tokens.map { tok ->
            tok to expandSynonyms(tok)
        }

        data class Scored(val item: SettingSearchItem, val score: Int, val matchedTokens: Int)
        val scoredList = mutableListOf<Scored>()

        for (idxItem in INDEXED_ITEMS) {
            var score = 0
            var matchedTokens = 0

            // 1. 完整原始查詢直接命中（單一關鍵字行為 100% 保持）
            if (idxItem.titleLower.contains(cleaned)) {
                score += 120
            } else if (idxItem.aliasesLower.any { it.contains(cleaned) }) {
                score += 100
            } else if (idxItem.fullHaystack.contains(cleaned)) {
                score += 70
            }

            // 2. 分詞逐 token 評分
            for ((rawTok, syns) in expandedPerToken) {
                var tokScore = 0
                if (idxItem.titleLower.contains(rawTok)) {
                    tokScore = maxOf(tokScore, 36)
                }
                if (idxItem.aliasesLower.any { it == rawTok }) {
                    tokScore = maxOf(tokScore, 34)
                } else if (idxItem.aliasesLower.any { it.contains(rawTok) }) {
                    tokScore = maxOf(tokScore, 26)
                }
                if (idxItem.descLower.contains(rawTok)) {
                    tokScore = maxOf(tokScore, 16)
                }

                // 若直接詞未命中，檢查同義詞表
                if (tokScore == 0) {
                    for (syn in syns) {
                        if (syn == rawTok) continue
                        if (idxItem.titleLower.contains(syn)) {
                            tokScore = maxOf(tokScore, 20)
                        } else if (idxItem.aliasesLower.any { it.contains(syn) }) {
                            tokScore = maxOf(tokScore, 16)
                        } else if (idxItem.descLower.contains(syn)) {
                            tokScore = maxOf(tokScore, 10)
                        }
                    }
                }

                if (tokScore > 0) {
                    matchedTokens++
                    score += tokScore
                }
            }

            if (matchedTokens >= 2) {
                score += matchedTokens * 12
            }

            if (score > 0) {
                scoredList.add(Scored(idxItem.item, score, matchedTokens))
            }
        }

        if (scoredList.isEmpty()) return emptyList()
        val maxScore = scoredList.maxOf { it.score }
        // 若已有高分精準命中（例如 ≥ 50 分），過濾掉僅靠單一泛用同義詞擦邊的低分雜訊
        val minThreshold = when {
            maxScore >= 100 -> 28
            maxScore >= 60 -> 22
            else -> 12
        }
        return scoredList
            .filter { it.score >= minThreshold }
            .sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.matchedTokens })
            .map { it.item }
    }

    /** 供 ChatScreen system prompt 動態產生可開關設定清單（含目前狀態） */
    fun buildToggleablePromptList(ctx: Context): String {
        val appCtx = ctx.applicationContext
        return ITEMS.filter { it.id in TOGGLEABLE_IDS }.joinToString("\n") { item ->
            val stateStr = if (getBooleanState(appCtx, item.id) == true) "開啟" else "關閉"
            "- id=${item.id} | 名稱=${item.title} | 目前狀態=$stateStr | 說明=${item.description}"
        }
    }

    data class NumericSettingSpec(
        val id: String,
        val title: String,
        val min: Int,
        val max: Int,
        val step: Int,
        val unit: String,
        val zeroLabel: String? = null,
        /** 選項型數值設定：以整數索引對應的選項標籤（如 0=一般條列、1=中央堆疊） */
        val valueLabels: List<String>? = null
    ) {
        fun clampAndSnap(raw: Int): Pair<Int, Boolean> {
            val wasClamped = raw < min || raw > max
            val clamped = raw.coerceIn(min, max)
            val snapped = if (step > 1) {
                (((clamped - min + step / 2) / step) * step + min).coerceIn(min, max)
            } else {
                clamped
            }
            return snapped to wasClamped
        }

        fun formatValue(value: Int): String =
            when {
                valueLabels != null -> valueLabels.getOrNull(value) ?: "$value $unit"
                value == 0 && zeroLabel != null -> zeroLabel
                else -> "$value $unit"
            }
    }

    val NUMERIC_SPECS: List<NumericSettingSpec> = listOf(
        NumericSettingSpec(
            id = "ai_chat_rag_limit",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_ai_chat_rag_limit_title),
            min = PhantomPrefs.MIN_CHAT_RAG_MESSAGE_LIMIT,
            max = PhantomPrefs.MAX_CHAT_RAG_MESSAGE_LIMIT,
            step = 10,
            unit = "則"
        ),
        NumericSettingSpec(
            id = "ai_daily_limit",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_ai_daily_limit_title),
            min = 0,
            max = 100,
            step = 10,
            unit = "次",
            zeroLabel = com.phantom.checker.PhantomApplication.str(R.string.common_unlimited)
        ),
        NumericSettingSpec(
            id = "notif_throttle",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_num_throttle_sec),
            min = 0,
            max = 300,
            step = 10,
            unit = "秒",
            zeroLabel = com.phantom.checker.PhantomApplication.str(R.string.catalog_num_zero_sec_off)
        ),
        NumericSettingSpec(
            id = "ai_min_trigger_length",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_ai_min_trigger_length_title),
            min = 1,
            max = 30,
            step = 1,
            unit = "字"
        ),
        NumericSettingSpec(
            id = "home_card_layout",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_personal_home_card_layout_title),
            min = 0,
            max = 1,
            step = 1,
            unit = "模式",
            valueLabels = listOf(com.phantom.checker.PhantomApplication.str(R.string.catalog_layout_list), com.phantom.checker.PhantomApplication.str(R.string.catalog_layout_stack))
        ),
        NumericSettingSpec(
            id = "notes_card_layout",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_personal_notes_card_layout_title),
            min = 0,
            max = 1,
            step = 1,
            unit = "模式",
            valueLabels = listOf(com.phantom.checker.PhantomApplication.str(R.string.catalog_layout_list), com.phantom.checker.PhantomApplication.str(R.string.catalog_layout_stack))
        ),
        NumericSettingSpec(
            id = "memory_normal_top_k",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_memory_normal_top_k_title),
            min = PhantomPrefs.MIN_MEMORY_NORMAL_TOP_K,
            max = PhantomPrefs.MAX_MEMORY_NORMAL_TOP_K,
            step = 1,
            unit = "筆"
        ),
        NumericSettingSpec(
            id = "memory_normal_per_item_chars",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_memory_normal_per_item_chars_title),
            min = PhantomPrefs.MIN_MEMORY_NORMAL_PER_ITEM_CHARS,
            max = PhantomPrefs.MAX_MEMORY_NORMAL_PER_ITEM_CHARS,
            step = 10,
            unit = "字"
        ),
        NumericSettingSpec(
            id = "memory_saver_top_k",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_memory_saver_top_k_title),
            min = PhantomPrefs.MIN_MEMORY_SAVER_TOP_K,
            max = PhantomPrefs.MAX_MEMORY_SAVER_TOP_K,
            step = 1,
            unit = "筆"
        ),
        NumericSettingSpec(
            id = "proactive_daily_cap",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_num_proactive_cap),
            min = 1,
            max = 20,
            step = 1,
            unit = "則"
        ),
        NumericSettingSpec(
            id = "calendar_insight_interval_minutes",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_calendar_insight_interval_minutes_title),
            min = 30,
            max = 180,
            step = 30,
            unit = "分鐘"
        ),
        NumericSettingSpec(
            id = "decision_confirm_threshold",
            title = com.phantom.checker.PhantomApplication.str(R.string.catalog_decision_confirm_threshold_title),
            min = 40,
            max = 85,
            step = 5,
            unit = "分"
        )
    )

    fun findNumericSpec(id: String): NumericSettingSpec? = NUMERIC_SPECS.firstOrNull { it.id == id }

    fun getNumericValue(ctx: Context, id: String): Int? {
        val appCtx = ctx.applicationContext
        return when (id) {
            "ai_chat_rag_limit" -> PhantomPrefs.getChatRagMessageLimit(appCtx)
            "ai_daily_limit" -> PhantomPrefs.getDailyAiLimit(appCtx)
            "notif_throttle" -> PhantomPrefs.getSameKeyThrottleSeconds(appCtx)
            "ai_min_trigger_length" -> PhantomPrefs.getAiMinTriggerLength(appCtx)
            "home_card_layout" -> when (PhantomPrefs.getHomeCardLayoutMode(appCtx)) {
                PhantomPrefs.HomeCardLayoutMode.LIST -> 0
                PhantomPrefs.HomeCardLayoutMode.CENTER -> 1
            }
            "notes_card_layout" -> when (PhantomPrefs.getNoteCardLayoutMode(appCtx)) {
                PhantomPrefs.NoteCardLayoutMode.LIST -> 0
                PhantomPrefs.NoteCardLayoutMode.CENTER -> 1
            }
            "memory_normal_top_k" -> PhantomPrefs.getMemoryNormalTopK(appCtx)
            "memory_normal_per_item_chars" -> PhantomPrefs.getMemoryNormalPerItemChars(appCtx)
            "memory_saver_top_k" -> PhantomPrefs.getMemorySaverTopK(appCtx)
            "proactive_daily_cap" -> PhantomPrefs.getProactiveDailyCap(appCtx)
            "calendar_insight_interval_minutes" -> PhantomPrefs.getCalendarInsightIntervalMinutes(appCtx)
            "decision_confirm_threshold" -> PhantomPrefs.getDecisionConfirmThreshold(appCtx)
            else -> null
        }
    }

    /**
     * 套用數值設定（走與 SettingsActivity 完全相同的 PhantomPrefs setter）。
     * 回傳 Triple(before, after, wasClamped)，若 id 不在可調整清單則回傳 null。
     */
    fun applyNumericSetting(ctx: Context, id: String, rawValue: Int): Triple<Int, Int, Boolean>? {
        val spec = findNumericSpec(id) ?: return null
        val appCtx = ctx.applicationContext
        val before = getNumericValue(appCtx, id) ?: return null
        val (target, wasClamped) = spec.clampAndSnap(rawValue)
        when (id) {
            "ai_chat_rag_limit" -> PhantomPrefs.setChatRagMessageLimit(appCtx, target)
            "ai_daily_limit" -> PhantomPrefs.setDailyAiLimit(appCtx, target)
            "notif_throttle" -> PhantomPrefs.setSameKeyThrottleSeconds(appCtx, target)
            "ai_min_trigger_length" -> PhantomPrefs.setAiMinTriggerLength(appCtx, target)
            "home_card_layout" -> PhantomPrefs.setHomeCardLayoutMode(
                appCtx,
                if (target == 1) PhantomPrefs.HomeCardLayoutMode.CENTER else PhantomPrefs.HomeCardLayoutMode.LIST
            )
            "notes_card_layout" -> PhantomPrefs.setNoteCardLayoutMode(
                appCtx,
                if (target == 1) PhantomPrefs.NoteCardLayoutMode.CENTER else PhantomPrefs.NoteCardLayoutMode.LIST
            )
            "memory_normal_top_k" -> PhantomPrefs.setMemoryNormalTopK(appCtx, target)
            "memory_normal_per_item_chars" -> PhantomPrefs.setMemoryNormalPerItemChars(appCtx, target)
            "memory_saver_top_k" -> PhantomPrefs.setMemorySaverTopK(appCtx, target)
            "proactive_daily_cap" -> PhantomPrefs.setProactiveDailyCap(appCtx, target)
            "calendar_insight_interval_minutes" -> {
                val snapped = listOf(30, 60, 120, 180).minByOrNull { kotlin.math.abs(it - target) } ?: 60
                PhantomPrefs.setCalendarInsightIntervalMinutes(appCtx, snapped)
                com.phantom.checker.service.CalendarInsightScheduler.scheduleOrUpdate(appCtx)
            }
            "decision_confirm_threshold" -> PhantomPrefs.setDecisionConfirmThreshold(appCtx, target)
            else -> return null
        }
        PhantomSettingsBus.notifyChanged()
        return Triple(before, target, wasClamped)
    }

    /** 供 ChatScreen system prompt 動態產生可調整數值設定清單（含範圍與目前值） */
    fun buildNumericPromptList(ctx: Context): String {
        val appCtx = ctx.applicationContext
        return NUMERIC_SPECS.joinToString("\n") { spec ->
            val current = getNumericValue(appCtx, spec.id) ?: spec.min
            val optionsPart = spec.valueLabels?.let { labels ->
                " | 選項=" + labels.mapIndexed { i, l -> "$i=$l" }.joinToString("/")
            } ?: ""
            "- id=${spec.id} | 名稱=${spec.title} | 範圍=${spec.min}–${spec.max}（步進 ${spec.step} ${spec.unit}） | 目前值=${spec.formatValue(current)}$optionsPart"
        }
    }

    /** 供 ChatScreen system prompt 動態產生全量設定清單摘要（含非開關項目，避免 AI 編造路徑） */
    fun buildAllSettingsPromptSummary(): String =
        ITEMS.joinToString("\n") { item ->
            "- id=${item.id} | 名稱=${item.title} | 說明=${item.description}"
        }

    /** 對全 catalog（id / 標題 / 別名 / 說明）進行關鍵字比對，回傳最相關的前 N 筆 */
    fun keywordSearch(query: String, limit: Int = 5): List<SettingSearchItem> {
        val tokens = query.trim().lowercase()
            .split(Regex("[\\s_\\-/、,，]+"))
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return ITEMS.mapNotNull { item ->
            val idLower = item.id.lowercase()
            val titleLower = item.title.lowercase()
            val descLower = item.description.lowercase()
            val aliasesLower = item.aliases.map { it.lowercase() }
            var score = 0
            tokens.forEach { tok ->
                if (idLower == tok) score += 10
                else if (idLower.contains(tok)) score += 4
                if (titleLower.contains(tok)) score += 6
                if (aliasesLower.any { it == tok }) score += 6
                else if (aliasesLower.any { it.contains(tok) }) score += 4
                if (descLower.contains(tok)) score += 2
            }
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * 用戶端確定性數值設定意圖解析（當模型未產出 setting_adjust 時作為 fallback）。
     * 必須同時命中「數字」與「數值設定關鍵詞」，避免一般訊息查詢誤觸。
     */
    fun resolveNumericIntentFallback(userText: String): Pair<NumericSettingSpec, Int>? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()

        val adjustNumMatch = Regex("(?:調成|調到|調整為|調整成|改成|改為|改到|設為|設成|設到|設定為|限制為|上限為?)\\s*(\\d+)")
            .find(lower)
        val allNumbers = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
        val targetValue = adjustNumMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: allNumbers.lastOrNull()
            ?: return null

        val hasNumericIntentKeyword = listOf(
            "載入", "閱讀", "筆數", "訊息數", "額度", "節流", "上限", "秒數", "記憶",
            "單次", "rag", "limit", "quota", "throttle", "調", "改", "設"
        ).any { lower.contains(it) }
        if (!hasNumericIntentKeyword) return null

        val explicitRules: List<Pair<String, List<String>>> = listOf(
            "memory_saver_top_k" to listOf(
                "省 token 記憶", "省token記憶", "省電記憶", "saver_top_k"
            ),
            "memory_normal_top_k" to listOf(
                "記憶注入筆數", "記憶筆數", "注入記憶筆數", "normal_top_k"
            ),
            "memory_normal_per_item_chars" to listOf(
                "記憶單筆字數", "記憶截斷字數", "記憶字數"
            ),
            "ai_chat_rag_limit" to listOf(
                "單次閱讀", "閱讀訊息", "單次載入", "載入訊息", "載入筆數",
                "閱讀筆數", "訊息數", "上下文", "chat_rag", "rag"
            ),
            "ai_daily_limit" to listOf(
                "每日額度", "每日 ai 額度", "每日ai額度", "每日上限",
                "ai 額度", "ai額度", "額度上限", "daily_limit", "quota"
            ),
            "notif_throttle" to listOf(
                "節流", "同 key", "同key", "重複通知", "throttle"
            )
        )
        for ((specId, phrases) in explicitRules) {
            if (phrases.any { lower.contains(it) }) {
                val spec = findNumericSpec(specId) ?: continue
                return spec to targetValue
            }
        }

        val numericIds = NUMERIC_SPECS.map { it.id }.toSet()
        val matchedFromCatalog = keywordSearch(text, limit = 5)
            .firstOrNull { it.id in numericIds }
            ?.let { findNumericSpec(it.id) }
        if (matchedFromCatalog != null) {
            return matchedFromCatalog to targetValue
        }
        return null
    }
}
