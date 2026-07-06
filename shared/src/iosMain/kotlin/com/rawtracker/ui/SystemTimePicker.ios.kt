package com.rawtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rawtracker.i18n.strings
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.UIKit.UIAction
import platform.UIKit.UIApplication
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.setTranslatesAutoresizingMaskIntoConstraints

@Composable
actual fun rememberSystemTimePicker(onTimePicked: (hour: Int, minute: Int) -> Unit): SystemTimePicker {
    val launcher = remember { IosTimePicker() }
    launcher.callback = onTimePicked
    return launcher
}

private class IosTimePicker : SystemTimePicker {
    var callback: (Int, Int) -> Unit = { _, _ -> }

    override fun show(hour: Int, minute: Int) {
        val top = topViewController() ?: return

        val container = UIViewController()
        container.modalPresentationStyle = UIModalPresentationPageSheet
        container.view.backgroundColor = UIColor(white = 1.0, alpha = 1.0)

        val picker = UIDatePicker()
        picker.datePickerMode = UIDatePickerMode.UIDatePickerModeTime
        picker.preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
        picker.setDate(dateFor(hour, minute), animated = false)
        picker.setTranslatesAutoresizingMaskIntoConstraints(false)

        val done = UIButton.buttonWithType(UIButtonTypeSystem)
        done.setTitle(strings.done, forState = UIControlStateNormal)
        done.setTranslatesAutoresizingMaskIntoConstraints(false)
        done.addAction(
            UIAction.actionWithHandler {
                val comps = NSCalendar.currentCalendar.components(
                    NSCalendarUnitHour or NSCalendarUnitMinute,
                    fromDate = picker.date
                )
                callback(comps.hour.toInt(), comps.minute.toInt())
                container.dismissViewControllerAnimated(true, completion = null)
            },
            forControlEvents = UIControlEventTouchUpInside
        )

        val cancel = UIButton.buttonWithType(UIButtonTypeSystem)
        cancel.setTitle(strings.cancel, forState = UIControlStateNormal)
        cancel.setTranslatesAutoresizingMaskIntoConstraints(false)
        cancel.addAction(
            UIAction.actionWithHandler { container.dismissViewControllerAnimated(true, completion = null) },
            forControlEvents = UIControlEventTouchUpInside
        )

        container.view.addSubview(picker)
        container.view.addSubview(done)
        container.view.addSubview(cancel)

        val guide = container.view.safeAreaLayoutGuide
        NSLayoutConstraint.activateConstraints(
            listOf(
                cancel.topAnchor.constraintEqualToAnchor(guide.topAnchor, constant = 12.0),
                cancel.leadingAnchor.constraintEqualToAnchor(container.view.leadingAnchor, constant = 16.0),
                done.topAnchor.constraintEqualToAnchor(guide.topAnchor, constant = 12.0),
                done.trailingAnchor.constraintEqualToAnchor(container.view.trailingAnchor, constant = -16.0),
                picker.centerXAnchor.constraintEqualToAnchor(container.view.centerXAnchor),
                picker.centerYAnchor.constraintEqualToAnchor(container.view.centerYAnchor)
            )
        )

        top.presentViewController(container, animated = true, completion = null)
    }

    private fun dateFor(hour: Int, minute: Int): NSDate {
        val comps = NSDateComponents()
        comps.hour = hour.toLong()
        comps.minute = minute.toLong()
        return NSCalendar.currentCalendar.dateFromComponents(comps) ?: NSDate()
    }

    @Suppress("DEPRECATION")
    private fun topViewController(): UIViewController? {
        val app = UIApplication.sharedApplication
        var root: UIViewController? = app.keyWindow?.rootViewController
        if (root == null) {
            root = (app.windows.firstOrNull() as? UIWindow)?.rootViewController
        }
        var top = root ?: return null
        while (top.presentedViewController != null) {
            top = top.presentedViewController!!
        }
        return top
    }
}
