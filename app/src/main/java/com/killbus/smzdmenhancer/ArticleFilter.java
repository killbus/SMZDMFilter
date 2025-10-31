package com.killbus.smzdmenhancer;

import com.killbus.smzdmenhancer.utils.Logger;

import java.util.Iterator;
import java.util.List;

/**
 * Core filtering logic for SMZDM articles
 * Based on the Rhino script functionality
 */
public class ArticleFilter {
    
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
                    int commentCount = getCommentCount(item);
                    if (commentCount < FilterConfig.COMMENT_THRESHOLD) {
                        String title = getArticleTitle(item);
                        String id = getArticleId(item);
                        String typeName = getArticleTypeName(item);
                        
                        Logger.logDroppedArticle(title, id, typeName);
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
                int commentCount = getCommentCount(article);
                if (commentCount < FilterConfig.COMMENT_THRESHOLD) {
                    String title = getArticleTitle(article);
                    String id = getArticleId(article);
                    String typeName = getArticleTypeName(parent);
                    
                    Logger.logDroppedArticle(title, id, typeName);
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
