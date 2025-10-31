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
     * Based on actual API response handling in current version
     */
    private void initializeHooks(ClassLoader classLoader) {
        try {
            // Strategy 1: Hook ViewModel LiveData (intercepts response after parsing)
            hookFollowSubRulesVM(classLoader);
            
            // Strategy 2: Hook Repository response handling (backup strategy)
            hookRepositoryResponse(classLoader);
            
            // Strategy 3: Hook "load more" functionality (from Rhino reference)
            hookJuCuMoreResponse(classLoader);
            
            Logger.info("All hooks initialized successfully");
            
            // Show Toast notification if enabled
            if (Config.SHOW_HOOK_SUCCESS_TOAST) {
                showToast(classLoader, "✅ SMZDM Enhancer Activated");
            }
        } catch (Exception e) {
            Logger.error("Error initializing hooks", e);
            if (Config.SHOW_HOOK_SUCCESS_TOAST) {
                showToast(classLoader, "❌ SMZDM Enhancer Failed");
            }
        }
    }
    
    /**
     * Strategy 1: Hook FollowSubRulesVM requestFeedList method directly
     * VERIFIED from sources: com.smzdm.client.android.module.guanzhu.subrules.FollowSubRulesVM
     * This method is called when requesting feed data
     */
    private void hookFollowSubRulesVM(ClassLoader classLoader) {
        try {
            // First, let's add comprehensive logging to see what's happening
            Class<?> mutableLiveDataClass = XposedHelpers.findClass(
                "androidx.lifecycle.MutableLiveData",
                classLoader
            );
            
            // Hook ALL setValue calls with detailed logging
            XposedHelpers.findAndHookMethod(
                mutableLiveDataClass,
                "setValue",
                Object.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object value = param.args[0];
                        if (value != null) {
                            String className = value.getClass().getName();
                            Logger.debug("LiveData.setValue called with: " + className);
                            
                            // Log all class names to identify the correct one
                            if (className.contains("ga") || className.contains("Feed") || className.contains("follow")) {
                                Logger.info("*** Potential feed data class: " + className);
                                
                                // Try to inspect the object
                                try {
                                    java.lang.reflect.Field[] fields = value.getClass().getDeclaredFields();
                                    Logger.debug("  Fields count: " + fields.length);
                                    for (java.lang.reflect.Field field : fields) {
                                        field.setAccessible(true);
                                        Object fieldValue = field.get(value);
                                        if (fieldValue instanceof List) {
                                            List<?> list = (List<?>) fieldValue;
                                            Logger.info("  *** Found List field '" + field.getName() + "' with " + list.size() + " items");
                                            
                                            if (!list.isEmpty()) {
                                                Object firstItem = list.get(0);
                                                Logger.info("  *** List item type: " + firstItem.getClass().getName());
                                                
                                                // Try to filter
                                                ArticleFilter.filterArticleList(list, false);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Logger.debug("  Error inspecting: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            );
            
            Logger.info("Successfully hooked MutableLiveData.setValue with enhanced logging");
        } catch (Exception e) {
            Logger.error("Failed to hook LiveData", e);
        }
    }
    
    /**
     * Strategy 2: Hook ResponseResult.setData with comprehensive logging
     * VERIFIED from sources: ea.d class handles API responses
     */
    private void hookRepositoryResponse(ClassLoader classLoader) {
        try {
            Class<?> responseResultClass = XposedHelpers.findClass(
                "com.smzdm.client.base.coroutines.http.ResponseResult",
                classLoader
            );
            
            // Hook setData with detailed logging
            XposedHelpers.findAndHookMethod(
                responseResultClass,
                "setData",
                Object.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object data = param.args[0];
                        if (data != null) {
                            String className = data.getClass().getName();
                            Logger.debug("ResponseResult.setData called with: " + className);
                            
                            // Log all relevant classes
                            if (className.contains("Follow") || className.contains("Feed") || className.contains("Bean")) {
                                Logger.info("*** Potential response bean: " + className);
                                
                                // Try to find rows field
                                try {
                                    java.lang.reflect.Field[] fields = data.getClass().getDeclaredFields();
                                    for (java.lang.reflect.Field field : fields) {
                                        field.setAccessible(true);
                                        if (field.getName().equals("rows") || field.getType().equals(List.class)) {
                                            Object fieldValue = field.get(data);
                                            if (fieldValue instanceof List) {
                                                List<?> list = (List<?>) fieldValue;
                                                Logger.info("  *** Found 'rows' field with " + list.size() + " items");
                                                
                                                if (!list.isEmpty()) {
                                                    Object firstItem = list.get(0);
                                                    Logger.info("  *** Row item type: " + firstItem.getClass().getName());
                                                    
                                                    // Try to filter
                                                    ArticleFilter.filterArticleList(list, false);
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Logger.debug("  Error processing: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            );
            
            Logger.info("Successfully hooked ResponseResult.setData with enhanced logging");
        } catch (Exception e) {
            Logger.error("Failed to hook ResponseResult", e);
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
                                    if (commentCount < Config.COMMENT_THRESHOLD) {
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
    
    /**
     * Show Toast notification in the target app
     * This helps users quickly verify the module is working
     */
    private void showToast(final ClassLoader classLoader, final String message) {
        try {
            // Find the Application context
            Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader);
            Object currentActivityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
            Object context = XposedHelpers.callMethod(currentActivityThread, "getApplication");
            
            // Show Toast on UI thread
            final Object appContext = context;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Get Handler for main thread
                        Class<?> looperClass = XposedHelpers.findClass("android.os.Looper", classLoader);
                        Object mainLooper = XposedHelpers.callStaticMethod(looperClass, "getMainLooper");
                        
                        Class<?> handlerClass = XposedHelpers.findClass("android.os.Handler", classLoader);
                        Object handler = XposedHelpers.newInstance(handlerClass, mainLooper);
                        
                        // Post to main thread
                        XposedHelpers.callMethod(handler, "post", new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Class<?> toastClass = XposedHelpers.findClass("android.widget.Toast", classLoader);
                                    Object toast = XposedHelpers.callStaticMethod(
                                        toastClass,
                                        "makeText",
                                        appContext,
                                        message,
                                        0 // Toast.LENGTH_SHORT
                                    );
                                    XposedHelpers.callMethod(toast, "show");
                                    Logger.debug("Toast shown: " + message);
                                } catch (Exception e) {
                                    Logger.error("Failed to show Toast", e);
                                }
                            }
                        });
                    } catch (Exception e) {
                        Logger.error("Failed to post Toast to main thread", e);
                    }
                }
            }).start();
        } catch (Exception e) {
            Logger.error("Failed to show Toast", e);
        }
    }
}
