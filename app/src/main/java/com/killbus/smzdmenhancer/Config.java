package com.killbus.smzdmenhancer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration class for SMZDM Enhancer module
 */
public class Config {
    // ========== Basic Filtering Configuration ==========
    
    /**
     * Minimum comment count threshold for articles
     * Articles with comment count below this value will be filtered out
     */
    public static int COMMENT_THRESHOLD = 3;
    
    /**
     * Minimum worthy count threshold
     * Articles with worthy count below this value will be filtered out
     * Set to 0 to disable this filter
     */
    public static int MIN_WORTHY_COUNT = 3;
    
    /**
     * Minimum worthy percentage threshold (0-100)
     * Calculated as: worthy / (worthy + unworthy) * 100
     * Articles below this percentage will be filtered out
     * Set to 0 to disable this filter
     * Example: 60 means at least 60% worthy ratio required
     */
    public static int MIN_WORTHY_PERCENTAGE = 60;
    
    /**
     * Enable debug mode for detailed logging
     */
    public static boolean DEBUG_MODE = true;
    
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
    public static boolean SHOW_FILTER_TOAST = true;
    
    // ========== Channel Filtering Configuration ==========
    
    /**
     * Channel filter mode
     * DISABLED: No channel filtering
     * WHITELIST: Only allow channels in CHANNEL_WHITELIST
     * BLACKLIST: Block channels in CHANNEL_BLACKLIST
     */
    public enum ChannelFilterMode {
        DISABLED,   // No channel filtering
        WHITELIST,  // Only allow specified channels
        BLACKLIST   // Block specified channels
    }
    
    /**
     * Current channel filter mode
     * Default: DISABLED (no channel filtering)
     */
    public static ChannelFilterMode CHANNEL_FILTER_MODE = ChannelFilterMode.WHITELIST;
    
    /**
     * Channel whitelist - only these channels will be shown
     * Common channels:
     * 1 = 好价 (Deals)
     * 11 = 原创 (Original)
     * 80 = 晒物 (Show & Tell)
     * Add channel IDs to this array to allow them
     */
    public static final Set<Integer> CHANNEL_WHITELIST = new HashSet<>(Arrays.asList(
        // Example: 1, 80  // Only show 好价 and 晒物
        1
    ));
    
    /**
     * Channel blacklist - these channels will be filtered out
     * Common channels:
     * 1 = 好价 (Deals)
     * 11 = 原创 (Original)
     * 80 = 晒物 (Show & Tell)
     * Add channel IDs to this array to block them
     */
    public static final Set<Integer> CHANNEL_BLACKLIST = new HashSet<>(Arrays.asList(
        // Example: 11  // Block 原创
    ));
    
    /**
     * Check if an article should be filtered based on channel ID
     * 
     * @param channelId The article's channel ID
     * @return true if article should be filtered out, false if should be kept
     */
    public static boolean shouldFilterByChannel(int channelId) {
        switch (CHANNEL_FILTER_MODE) {
            case WHITELIST:
                // In whitelist mode, filter out if NOT in whitelist
                return !CHANNEL_WHITELIST.contains(channelId);
            
            case BLACKLIST:
                // In blacklist mode, filter out if IN blacklist
                return CHANNEL_BLACKLIST.contains(channelId);
            
            case DISABLED:
            default:
                // No channel filtering
                return false;
        }
    }
}
