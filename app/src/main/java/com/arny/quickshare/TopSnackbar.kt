package com.arny.quickshare

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

object TopSnackbar {
    fun make(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT): Snackbar {
        val snackbar = Snackbar.make(view, message, duration)
        val snackbarView = snackbar.view

        // Изменяем параметры расположения для отображения сверху
        val params = snackbarView.layoutParams
        when (params) {
            is CoordinatorLayout.LayoutParams -> {
                params.gravity = Gravity.TOP
                params.topMargin = 12
            }
            is FrameLayout.LayoutParams -> {
                params.gravity = Gravity.TOP
                params.topMargin = 12
            }
        }
        snackbarView.layoutParams = params

        // Устанавливаем цвет фона
        snackbarView.setBackgroundColor(ContextCompat.getColor(view.context, R.color.snackbar_background))
        
        return snackbar
    }
} 