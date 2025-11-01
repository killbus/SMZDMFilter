package com.killbus.smzdmenhancer;

import com.killbus.smzdmenhancer.utils.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

/**
 * Core filtering logic for SMZDM articles
 * Based on the Rhino script functionality
 */
public class ArticleFilter {

    /**
     * Filter a JSON response string based on various criteria.
     * This method is used for direct response hooking before JSON deserialization.
     *
     * @param jsonStr The raw JSON response string.
     * @return The filtered JSON string, or null if no changes were made.
     */
    public static String filterJsonResponse(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);

            if (!root.has("data")) return null;
            JSONObject data = root.getJSONObject("data");
            if (!data.has("rows")) return null;

            JSONArray rows = data.getJSONArray("rows");
            JSONArray filteredRows = new JSONArray();
            int totalDropped = 0;

            for (int i = 0; i < rows.length(); i++) {
                JSONObject article = rows.getJSONObject(i);
                boolean shouldFilter = false;
                String filterReason = "";

                // Check comment count
                int commentCount = 0;
                if (article.has("article_comment")) {
                    String commentStr = article.optString("article_comment", "0");
                    try {
                        commentCount = Integer.parseInt(commentStr);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }

                if (commentCount < Config.COMMENT_THRESHOLD) {
                    shouldFilter = true;
                    filterReason = "comments:" + commentCount;
                }

                if (shouldFilter) {
                    String title = article.optString("article_title", "Unknown");
                    String id = article.optString("article_id", "N/A");
                    Logger.logDroppedArticle(title, id, "JSONFilter[" + filterReason + "]");
                    totalDropped++;
                } else {
                    filteredRows.put(article);
                }
            }

            if (totalDropped > 0) {
                Logger.info(String.format("Filtered JSON: %d dropped, %d kept",
                    totalDropped, filteredRows.length()));

                // If all articles were dropped, add the last one back to prevent breaking the UI
                if (filteredRows.length() == 0 && rows.length() > 0) {
                    JSONObject lastArticle = rows.getJSONObject(rows.length() - 1);
                    filteredRows.put(lastArticle);
                    String title = lastArticle.optString("article_title", "Unknown");
                    Logger.info("All articles dropped, keeping the last one to prevent UI issues: " + title);
                }

                data.put("rows", filteredRows);
                return root.toString();
            }

            return null; // No changes
        } catch (Exception e) {
            Logger.error("Error parsing/filtering JSON", e);
            return null;
        }
    }
    
    /**
     * Filter a list of FollowItemBean objects based on comment count
     * This method modifies the list in place
     * 
     * @param items List of items to filter
     * @param isRefresh Whether this is a refresh operation (affects minimum items to keep)
     * @return Number of items filtered out
     */
    public static int filterArticleList(List<?> items, boolean isRefresh) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        
        int totalDropped = 0;
        Iterator<?> iterator = items.iterator();
        Object lastItem = items.get(items.size() - 1);
        
        while (iterator.hasNext()) {
            Object item = iterator.next();
            
            try {
                // Get article_list using reflection
                List<?> articleList = getArticleList(item);
                
                if (articleList != null && !articleList.isEmpty()) {
                    // Filter nested article_list
                    int dropped = filterNestedArticles(item, articleList);
                    totalDropped += dropped;
                    
                    // Remove parent if all children are filtered out
                    if (articleList.isEmpty()) {
                        iterator.remove();
                    }
                } else {
                    // Filter top-level item
                    boolean shouldFilter = false;
                    String filterReason = "";
                    
                    // Check channel filter first
                    int channelId = getChannelId(item);
                    if (Config.shouldFilterByChannel(channelId)) {
                        shouldFilter = true;
                        filterReason = "channel:" + channelId;
                    }
                    
                    // Check comment count if not already filtered
                    if (!shouldFilter) {
                        int commentCount = getCommentCount(item);
                        if (commentCount < Config.COMMENT_THRESHOLD) {
                            shouldFilter = true;
                            filterReason = "comments:" + commentCount;
                        }
                    }
                    
                    // Check worthy count if not already filtered
                    if (!shouldFilter && Config.MIN_WORTHY_COUNT > 0) {
                        int worthyCount = getWorthyCount(item);
                        if (worthyCount < Config.MIN_WORTHY_COUNT) {
                            shouldFilter = true;
                            filterReason = "worthy:" + worthyCount;
                        }
                    }
                    
                    // Check worthy percentage if not already filtered
                    if (!shouldFilter && Config.MIN_WORTHY_PERCENTAGE > 0) {
                        int worthyCount = getWorthyCount(item);
                        int unworthyCount = getUnworthyCount(item);
                        int percentage = calculateWorthyPercentage(worthyCount, unworthyCount);
                        if (percentage < Config.MIN_WORTHY_PERCENTAGE) {
                            shouldFilter = true;
                            filterReason = "worthy%:" + percentage;
                        }
                    }
                    
                    if (shouldFilter) {
                        String title = getArticleTitle(item);
                        String id = getArticleId(item);
                        String typeName = getArticleTypeName(item);
                        
                        Logger.logDroppedArticle(title, id, typeName + "[" + filterReason + "]");
                        iterator.remove();
                        totalDropped++;
                    }
                }
            } catch (Exception e) {
                Logger.error("Error filtering item", e);
            }
        }
        
        // Handle empty list - add placeholder if needed
        if (items.isEmpty() && lastItem != null) {
            handleEmptyList(items, lastItem);
        }
        
        // Ensure minimum items for refresh functionality
        if (isRefresh) {
            ensureMinimumItems(items);
        }
        
        Logger.logFilterResult(totalDropped, items.size());
        return totalDropped;
    }
    
    /**
     * Filter nested articles within a FollowItemBean
     */
    private static int filterNestedArticles(Object parent, List<?> articleList) {
        if (articleList == null || articleList.isEmpty()) {
            return 0;
        }
        
        int dropped = 0;
        Iterator<?> iterator = articleList.iterator();
        
        while (iterator.hasNext()) {
            Object article = iterator.next();
            
            try {
                boolean shouldFilter = false;
                String filterReason = "";
                
                // Check channel filter first
                int channelId = getChannelId(article);
                if (Config.shouldFilterByChannel(channelId)) {
                    shouldFilter = true;
                    filterReason = "channel:" + channelId;
                }
                
                // Check comment count if not already filtered
                if (!shouldFilter) {
                    int commentCount = getCommentCount(article);
                    if (commentCount < Config.COMMENT_THRESHOLD) {
                        shouldFilter = true;
                        filterReason = "comments:" + commentCount;
                    }
                }
                
                // Check worthy count if not already filtered
                if (!shouldFilter && Config.MIN_WORTHY_COUNT > 0) {
                    int worthyCount = getWorthyCount(article);
                    if (worthyCount < Config.MIN_WORTHY_COUNT) {
                        shouldFilter = true;
                        filterReason = "worthy:" + worthyCount;
                    }
                }
                
                // Check worthy percentage if not already filtered
                if (!shouldFilter && Config.MIN_WORTHY_PERCENTAGE > 0) {
                    int worthyCount = getWorthyCount(article);
                    int unworthyCount = getUnworthyCount(article);
                    int percentage = calculateWorthyPercentage(worthyCount, unworthyCount);
                    if (percentage < Config.MIN_WORTHY_PERCENTAGE) {
                        shouldFilter = true;
                        filterReason = "worthy%:" + percentage;
                    }
                }
                
                if (shouldFilter) {
                    String title = getArticleTitle(article);
                    String id = getArticleId(article);
                    String typeName = getArticleTypeName(parent);
                    
                    Logger.logDroppedArticle(title, id, typeName + "[" + filterReason + "]");
                    iterator.remove();
                    dropped++;
                }
            } catch (Exception e) {
                Logger.error("Error filtering nested article", e);
            }
        }
        
        return dropped;
    }
    
    /**
     * Get comment count from an article object using reflection
     */
    private static int getCommentCount(Object article) {
        try {
            // Try article_comment field (String type)
            Object commentObj = getField(article, "article_comment");
            if (commentObj != null) {
                if (commentObj instanceof String) {
                    String commentStr = (String) commentObj;
                    if (commentStr != null && !commentStr.isEmpty()) {
                        return Integer.parseInt(commentStr);
                    }
                } else if (commentObj instanceof Integer) {
                    return (Integer) commentObj;
                }
            }
            
            // Fallback: try article_interaction object
            Object interaction = getField(article, "article_interaction");
            if (interaction != null) {
                commentObj = getField(interaction, "article_comment");
                if (commentObj instanceof String) {
                    String commentStr = (String) commentObj;
                    if (commentStr != null && !commentStr.isEmpty()) {
                        return Integer.parseInt(commentStr);
                    }
                }
            }
        } catch (Exception e) {
            Logger.debug("Could not get comment count: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Get article_list from a FollowItemBean
     */
    private static List<?> getArticleList(Object item) {
        try {
            Object listObj = getField(item, "article_list");
            if (listObj instanceof List) {
                return (List<?>) listObj;
            }
        } catch (Exception e) {
            Logger.debug("Could not get article_list: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get article title
     */
    private static String getArticleTitle(Object article) {
        try {
            Object title = getField(article, "article_title");
            return title != null ? title.toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * Get article ID
     */
    private static String getArticleId(Object article) {
        try {
            Object id = getField(article, "article_id");
            return id != null ? id.toString() : "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }
    
    /**
     * Get article type name
     */
    private static String getArticleTypeName(Object article) {
        try {
            Object typeName = getField(article, "article_type_name");
            return typeName != null ? typeName.toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * Get article channel ID
     */
    private static int getChannelId(Object article) {
        try {
            Object channelId = getField(article, "article_channel_id");
            if (channelId instanceof Integer) {
                return (Integer) channelId;
            } else if (channelId instanceof String) {
                return Integer.parseInt((String) channelId);
            }
        } catch (Exception e) {
            Logger.debug("Could not get channel ID: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Get worthy count from article
     */
    private static int getWorthyCount(Object article) {
        try {
            Object worthy = getField(article, "article_worthy");
            if (worthy instanceof Integer) {
                return (Integer) worthy;
            } else if (worthy instanceof String) {
                String worthyStr = (String) worthy;
                if (worthyStr != null && !worthyStr.isEmpty()) {
                    return Integer.parseInt(worthyStr);
                }
            }
        } catch (Exception e) {
            Logger.debug("Could not get worthy count: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Get unworthy count from article
     */
    private static int getUnworthyCount(Object article) {
        try {
            Object unworthy = getField(article, "article_unworthy");
            if (unworthy instanceof Integer) {
                return (Integer) unworthy;
            } else if (unworthy instanceof String) {
                String unworthyStr = (String) unworthy;
                if (unworthyStr != null && !unworthyStr.isEmpty()) {
                    return Integer.parseInt(unworthyStr);
                }
            }
        } catch (Exception e) {
            Logger.debug("Could not get unworthy count: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Calculate worthy percentage
     * @param worthy Worthy count
     * @param unworthy Unworthy count
     * @return Percentage (0-100), or 100 if total is 0
     */
    private static int calculateWorthyPercentage(int worthy, int unworthy) {
        int total = worthy + unworthy;
        if (total == 0) {
            return 100; // If no votes, consider it 100% to not filter
        }
        return (int) ((worthy * 100.0) / total);
    }
    
    /**
     * Get field value using reflection
     */
    private static Object getField(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException e) {
            // Try superclass
            try {
                java.lang.reflect.Field field = obj.getClass().getSuperclass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception ex) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Handle empty list by adding a placeholder
     */
    private static void handleEmptyList(List<?> items, Object lastItem) {
        try {
            // Create a placeholder item based on lastItem structure
            // This ensures the list is not completely empty for refresh functionality
            Logger.debug("List is empty after filtering, considering placeholder");
            // Note: Actual placeholder creation requires knowing the exact class
            // and having access to its constructor, which we'll handle in the hook
        } catch (Exception e) {
            Logger.error("Error handling empty list", e);
        }
    }
    
    /**
     * Ensure minimum items in list for refresh functionality
     */
    private static void ensureMinimumItems(List<?> items) {
        // This will be implemented in the hook where we have access to the actual class
        Logger.debug("Current list size after filtering: " + items.size());
    }
}
