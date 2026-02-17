package com.obsidianwidget

import android.content.Context
import android.content.Intent
import android.view.GestureDetector
import android.view.MotionEvent

class NoteGestureHandler(
    private val context: Context,
    private val appWidgetId: Int
) : GestureDetector.SimpleOnGestureListener() {

    companion object {
        const val ACTION_PAGE_CHANGE = "com.obsidianwidget.ACTION_PAGE_CHANGE"
        const val ACTION_SCROLL = "com.obsidianwidget.ACTION_SCROLL"
        const val ACTION_OPEN_NOTE = "com.obsidianwidget.ACTION_OPEN_NOTE"
        const val ACTION_TOGGLE_COMPACT = "com.obsidianwidget.ACTION_TOGGLE_COMPACT"
        const val ACTION_SHOW_OVERLAY = "com.obsidianwidget.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.obsidianwidget.ACTION_HIDE_OVERLAY"

        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_WIDGET_ID = "appWidgetId"

        const val DIRECTION_LEFT = "LEFT"
        const val DIRECTION_RIGHT = "RIGHT"
        const val DIRECTION_UP = "UP"
        const val DIRECTION_DOWN = "DOWN"

        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
        private const val OVERSHOOT_THRESHOLD_DP = 24f
    }

    private var isAtScrollBoundary = false

    fun setAtScrollBoundary(atBoundary: Boolean) {
        isAtScrollBoundary = atBoundary
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 == null) return false

        val diffX = e2.x - e1.x
        val diffY = e2.y - e1.y

        val absDiffX = Math.abs(diffX)
        val absDiffY = Math.abs(diffY)

        // Vertical scroll takes priority over horizontal page swipe
        if (absDiffY > absDiffX) {
            // Vertical fling — scroll
            if (absDiffY > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                val direction = if (diffY > 0) DIRECTION_DOWN else DIRECTION_UP
                sendBroadcast(ACTION_SCROLL, direction)
                return true
            }
        } else {
            // Horizontal fling — only if at scroll boundary (overshoot logic)
            if (absDiffX > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (isAtScrollBoundary || absDiffY < OVERSHOOT_THRESHOLD_DP) {
                    val direction = if (diffX > 0) DIRECTION_RIGHT else DIRECTION_LEFT
                    sendBroadcast(ACTION_PAGE_CHANGE, direction)
                    return true
                }
            }
        }
        return false
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        sendBroadcast(ACTION_OPEN_NOTE, null)
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        sendBroadcast(ACTION_TOGGLE_COMPACT, null)
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        sendBroadcast(ACTION_SHOW_OVERLAY, null)
    }

    private fun sendBroadcast(action: String, direction: String?) {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
            if (direction != null) {
                putExtra(EXTRA_DIRECTION, direction)
            }
        }
        context.sendBroadcast(intent)
    }
}
