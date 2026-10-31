package com.astanabesta.midrop

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.focus.util.FocusUtils

/** Optional HyperNotification integration. Android notifications remain the fallback. */
object HyperIslandDecorator {
    @Volatile private var granted = false

    /** Xiaomi's provider query may be slow; keep it off the NFC and notification threads. */
    @JvmStatic fun refresh(context: Context, callback: Runnable?) {
        val app = context.applicationContext
        Thread {
            val allowed = runCatching { FocusUtils.hasFocusPermission(app) }.getOrDefault(false)
            granted = allowed
            if (callback != null) Handler(Looper.getMainLooper()).post(callback)
        }.start()
    }

    @JvmStatic fun available(): Boolean = granted

    /** Never generate vendor templates unless Xiaomi reports access for this app. */
    @JvmStatic fun extras(context: Context, description: String, percent: Int): Bundle {
        if (!granted) return Bundle()
        return runCatching {
            FocusNotification.buildV3 {
                business = "other"
                updatable = true
                enableFloat = false
                filterWhenNoPermission = false
                ticker = if (percent < 0) "MIdrop" else "$percent%"
                val logo = createPicture("midrop_logo", Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
                tickerPic = logo
                baseInfo {
                    type = 1
                    title = "MIdrop"
                    content = description
                }
                island {
                    islandProperty = 1
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            picInfo { type = 1; pic = logo }
                        }
                        if (percent >= 0) {
                            progressTextInfo {
                                progressInfo { progress = percent.coerceIn(0, 100); colorReach = "#8AA9FF" }
                                textInfo { title = "$percent%" }
                            }
                        } else {
                            imageTextInfoRight { type = 2; textInfo { title = "Sharing" } }
                        }
                    }
                    smallIslandArea { picInfo { type = 1; pic = logo } }
                }
            }
        }.getOrElse { Bundle() }
    }
}
