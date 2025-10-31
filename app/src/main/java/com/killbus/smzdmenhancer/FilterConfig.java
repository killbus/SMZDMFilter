package com.killbus.smzdmenhancer;

/**
 * Configuration class for article filtering
 */
public class FilterConfig {
    /**
     * Minimum comment count threshold for articles
     * Articles with comment count below this value will be filtered out
     */
    public static int COMMENT_THRESHOLD = 3;
    
    /**
     * Enable debug mode for detailed logging
     */
    public static boolean DEBUG_MODE = false;
    
    /**
     * Enable logging of filtered articles
     */
    public static boolean ENABLE_LOGGING = true;
    
    /**
     * Minimum items to keep in list after filtering (for refresh functionality)
     */
    public static int MIN_ITEMS_TO_KEEP = 5;
}
