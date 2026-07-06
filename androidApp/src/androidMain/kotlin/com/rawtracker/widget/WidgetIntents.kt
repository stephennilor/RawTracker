package com.rawtracker.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rawtracker.MainActivity

internal fun widgetLaunchIntent(context: Context, deepLink: String? = null): Intent =
    Intent(context, MainActivity::class.java).apply {
        if (deepLink != null) {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deepLink)
        }
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
