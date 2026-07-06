package com.rawtracker.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rawtracker.design.RawIcons

/** 1x1 home-screen widget: tap to jump straight into camera capture. */
class CameraWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        val intent = widgetLaunchIntent(context, "rawtracker://capture")
        val canvas = ColorProvider(Color(data.canvas))
        val ink = ColorProvider(Color(data.ink))
        val inkDim = ColorProvider(Color(data.ink).copy(alpha = 0.62f))
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(canvas)
                    .cornerRadius(20.dp)
                    .padding(8.dp)
                    .clickable(actionStartActivity(intent)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = GlanceModifier.width(58.dp).height(44.dp).background(ink).cornerRadius(12.dp).padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        VectorIconImage(
                            context = context,
                            vector = RawIcons.camera,
                            color = Color(data.canvas),
                            width = 52.dp,
                            height = 38.dp,
                            fillRatio = 0.82f,
                            contentDescription = "Open camera",
                        )
                    }
                    Spacer(GlanceModifier.height(5.dp))
                    Text(
                        "RAW",
                        maxLines = 1,
                        style = TextStyle(color = inkDim, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    )
                }
            }
        }
    }
}

class CameraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CameraWidget()
}
