package com.rawtracker.widget

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.lifecycle.lifecycleScope
import com.rawtracker.MainActivity
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Debug-only harness that renders the real Glance [ProgressContent] at a matrix of widget
 * DpSizes, rasterises each to a PNG under getExternalFilesDir("widget_preview"), and shows
 * them on screen. Lets us iterate on widget visuals at every launcher size without fighting
 * the non-scriptable launcher resize handles.
 *
 * Launch:  adb shell am start -n com.rawtracker.app/com.rawtracker.widget.WidgetPreviewActivity
 * Pull:    adb pull /sdcard/Android/data/com.rawtracker.app/files/widget_preview
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetPreviewActivity : ComponentActivity() {

    private val sizes: List<Pair<String, DpSize>> = listOf(
        "1x1" to DpSize(72.dp, 72.dp),
        "2x1" to DpSize(150.dp, 72.dp),
        "3x1" to DpSize(228.dp, 72.dp),
        "4x1" to DpSize(310.dp, 72.dp),
        "2x2" to DpSize(150.dp, 150.dp),
        "3x2" to DpSize(228.dp, 150.dp),
        "4x2" to DpSize(310.dp, 150.dp),
        "1x3" to DpSize(72.dp, 228.dp),
        "2x3" to DpSize(150.dp, 228.dp),
        "3x3" to DpSize(228.dp, 228.dp),
        "4x3" to DpSize(310.dp, 228.dp),
        "4x4" to DpSize(310.dp, 310.dp)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val liveData = WidgetData.load(this)
        val stressData = liveData.copy(
            cal = 2450,
            protein = 148,
            carbs = 310,
            fat = 95,
            water = 2750,
            goalCal = 3200,
            goalProtein = 165,
            goalCarbs = 250,
            goalFat = 80
        )
        val scenarios = listOf("live" to liveData, "stress" to stressData)
        val addIntent = Intent(Intent.ACTION_VIEW, Uri.parse("rawtracker://add")).setPackage(packageName)
        val waterIntent = Intent(Intent.ACTION_VIEW, Uri.parse("rawtracker://water")).setPackage(packageName)
        val openIntent = Intent(this, MainActivity::class.java)
        val density = resources.displayMetrics.density

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF2A2A2A.toInt())
        }
        val scroll = ScrollView(this).apply { addView(column) }
        setContentView(scroll)

        val outDir = File(getExternalFilesDir(null), "widget_preview").apply {
            deleteRecursively()
            mkdirs()
        }

        lifecycleScope.launch {
            val grv = GlanceRemoteViews()
            for ((scenario, data) in scenarios) {
                for ((label, size) in sizes) {
                    val wPx = (size.width.value * density).toInt()
                    val hPx = (size.height.value * density).toInt()

                    val rv = grv.compose(this@WidgetPreviewActivity, size) {
                        ProgressContent(this@WidgetPreviewActivity, data, addIntent, waterIntent, openIntent)
                    }.remoteViews

                    val view: View = rv.apply(this@WidgetPreviewActivity, column)
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY)
                    )
                    view.layout(0, 0, wPx, hPx)

                    val bitmap = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    FileOutputStream(File(outDir, "$scenario-$label.png")).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }

                    column.addView(TextView(this@WidgetPreviewActivity).apply {
                        text = "$scenario-$label  (${size.width.value.toInt()}x${size.height.value.toInt()}dp)"
                        setTextColor(0xFFBBBBBB.toInt())
                        setPadding(0, 20, 0, 6)
                    })
                    column.addView(ImageView(this@WidgetPreviewActivity).apply {
                        setImageBitmap(bitmap)
                        layoutParams = LinearLayout.LayoutParams(wPx, hPx)
                    })
                }
            }
            File(outDir, "DONE.txt").writeText("ok")
        }
    }
}
