package com.datalens.app.domain.model

import android.graphics.Bitmap

enum class AlertKind {
    /** App used far more today than its recent daily average. */
    HIGH_USAGE_VS_AVERAGE,

    /** App used significant data with no recorded usage in the previous 7 days. */
    NEW_SIGNIFICANT_USAGE,

    /** A single app consumed a large share of today's total traffic. */
    DOMINANT_SHARE,
}

data class UsageAlert(
    val kind: AlertKind,
    val uid: Int,
    val packageName: String,
    val appName: String,
    val icon: Bitmap?,
    val title: String,
    val description: String,
    /** Higher = more severe; used for ordering. */
    val severity: Int,
)
