package com.killbus.smzdmenhancer;

/**
 * Configuration class for SMZDM Enhancer module
 */
public class Config {
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
    
    /**
     * Show Toast notification when hooks are successfully initialized
     * Useful for quickly verifying the module is working
     */
    public static boolean SHOW_HOOK_SUCCESS_TOAST = true;
    
    /**
     * Show Toast notification when filtering articles
     * Useful for seeing the filter in action
     */
    public static boolean SHOW_FILTER_TOAST = false;
}
