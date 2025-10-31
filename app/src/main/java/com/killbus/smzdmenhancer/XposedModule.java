package com.killbus.smzdmenhancer;

import com.killbus.smzdmenhancer.utils.Logger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.List;

/**
 * Main Xposed module entry point
 * Implements hybrid hooking strategy for maximum compatibility
 */
public class XposedModule implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.smzdm.client.android";
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        
        Logger.info("SMZDM Enhancer Module loaded for package: " + lpparam.packageName);
        
        // Hook when ActivityThread initializes the app to get classloader
        XposedHelpers.findAndHookMethod(
            "android.app.ActivityThread",
            lpparam.classLoader,
            "performLaunchActivity",
            "android.app.ActivityThread$ActivityClientRecord",
            "android.content.Intent",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // Get app's classloader
                    Object mInitialApplication = XposedHelpers.getObjectField(param.thisObject, "mInitialApplication");
                    ClassLoader appClassLoader = (ClassLoader) XposedHelpers.callMethod(mInitialApplication, "getClassLoader");
                    
                    // Initialize hooks with app's classloader
                    initializeHooks(appClassLoader);
                }
            }
        );
    }
    
    /**
     * Initialize all hooks with the app's classloader
     */
    private void initializeHooks(ClassLoader classLoader) {
        try {
            // Strategy 1: Hook at data model level (most stable)
            hookFollowItemBeanData(classLoader);
            
            // Strategy 2: Hook "load more" functionality
            hookJuCuMoreResponse(classLoader);
            
            Logger.info("All hooks initialized successfully");
        } catch (Exception e) {
            Logger.error("Error initializing hooks", e);
        }
    }
    
    /**
     * Strategy 1: Hook FollowItemBean$Data.getRows()
     * This catches data immediately after JSON parsing
     */
    private void hookFollowItemBeanData(ClassLoader classLoader) {
        try {
            Class<?> dataClass = XposedHelpers.findClass(
                "com.smzdm.client.android.bean.FollowItemBean$Data",
                classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                dataClass,
                "getRows",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        List<?> rows = (List<?>) param.getResult();
                        if (rows != null && !rows.isEmpty()) {
                            Logger.debug("Hooking getRows() - filtering " + rows.size() + " items");
                            ArticleFilter.filterArticleList(rows, false);
                        }
                    }
                }
            );
            
            Logger.info("Successfully hooked FollowItemBean$Data.getRows()");
        } catch (Exception e) {
            Logger.error("Failed to hook FollowItemBean$Data", e);
        }
    }
    
    /**
     * Strategy 2: Hook CommonMessageDetailBean for "load more" functionality
     * VERIFIED from decompiled sources: com.smzdm.client.android.bean.CommonMessageDetailBean
     * Used by: CommonMessageDetailFragment for "list_more_jucu_info" endpoint
     */
    private void hookJuCuMoreResponse(ClassLoader classLoader) {
        try {
            // Hook the VERIFIED class from decompiled sources
            Class<?> commonMessageDetailDataClass = XposedHelpers.findClass(
                "com.smzdm.client.android.bean.CommonMessageDetailBean$Data",
                classLoader
            );
            
            // Hook getRows() which returns List<Article>
            XposedHelpers.findAndHookMethod(
                commonMessageDetailDataClass,
                "getRows",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        List<?> articleList = (List<?>) param.getResult();
                        if (articleList != null && !articleList.isEmpty()) {
                            Logger.debug("Hooking CommonMessageDetailBean$Data.getRows() - filtering " + articleList.size() + " items");
                            
                            // Filter the articles
                            int totalDropped = 0;
                            java.util.Iterator<?> iterator = articleList.iterator();
                            while (iterator.hasNext()) {
                                Object article = iterator.next();
                                try {
                                    int commentCount = getCommentCount(article);
                                    if (commentCount < FilterConfig.COMMENT_THRESHOLD) {
                                        String title = getArticleTitle(article);
                                        String id = getArticleId(article);
                                        Logger.logDroppedArticle(title, id, "CommonMessageDetail");
                                        iterator.remove();
                                        totalDropped++;
                                    }
                                } catch (Exception e) {
                                    Logger.debug("Error filtering article: " + e.getMessage());
                                }
                            }
                            
                            if (totalDropped > 0) {
                                Logger.info(String.format("CommonMessageDetailBean filtered: %d dropped, %d remaining", 
                                    totalDropped, articleList.size()));
                            }
                        }
                    }
                }
            );
            
            Logger.info("Successfully hooked CommonMessageDetailBean$Data");
        } catch (Exception e) {
            Logger.error("Failed to hook CommonMessageDetailBean (non-critical)", e);
        }
    }
    
    /**
     * Helper method to get comment count from an article
     */
    private int getCommentCount(Object article) {
        try {
            java.lang.reflect.Field field = article.getClass().getDeclaredField("article_comment");
            field.setAccessible(true);
            Object commentObj = field.get(article);
            
            if (commentObj instanceof String) {
                String commentStr = (String) commentObj;
                return commentStr.isEmpty() ? 0 : Integer.parseInt(commentStr);
            } else if (commentObj instanceof Integer) {
                return (Integer) commentObj;
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }
    
    /**
     * Helper method to get article title
     */
    private String getArticleTitle(Object article) {
        try {
            java.lang.reflect.Field field = article.getClass().getDeclaredField("article_title");
            field.setAccessible(true);
            Object title = field.get(article);
            return title != null ? title.toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * Helper method to get article ID
     */
    private String getArticleId(Object article) {
        try {
            java.lang.reflect.Field field = article.getClass().getDeclaredField("article_id");
            field.setAccessible(true);
            Object id = field.get(article);
            return id != null ? id.toString() : "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }
}
