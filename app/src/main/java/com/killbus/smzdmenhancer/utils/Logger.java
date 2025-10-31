package com.killbus.smzdmenhancer.utils;

import com.killbus.smzdmenhancer.FilterConfig;
import de.robv.android.xposed.XposedBridge;

/**
 * Logging utility for the Xposed module
 */
public class Logger {
    private static final String TAG = "SMZDMEnhancer";
    
    /**
     * Log info message
     */
    public static void info(String message) {
        if (FilterConfig.ENABLE_LOGGING) {
            XposedBridge.log(TAG + ": " + message);
        }
    }
    
    /**
     * Log debug message (only in debug mode)
     */
    public static void debug(String message) {
        if (FilterConfig.DEBUG_MODE) {
            XposedBridge.log(TAG + " [DEBUG]: " + message);
        }
    }
    
    /**
     * Log error message
     */
    public static void error(String message, Throwable throwable) {
        XposedBridge.log(TAG + " [ERROR]: " + message);
        if (throwable != null) {
            XposedBridge.log(throwable);
        }
    }
    
    /**
     * Log filtering result
     */
    public static void logFilterResult(int totalDropped, int remaining) {
        if (FilterConfig.ENABLE_LOGGING && totalDropped > 0) {
            info(String.format("Result was filtered. %d dropped. Keep %d", totalDropped, remaining));
        }
    }
    
    /**
     * Log dropped article
     */
    public static void logDroppedArticle(String title, String id, String type) {
        if (FilterConfig.DEBUG_MODE) {
            debug(String.format("Drop \"%s (%s)\" from \"%s\"", title, id, type));
        }
    }
}
