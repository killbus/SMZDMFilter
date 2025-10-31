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
     * Strategy 1: Hook FollowSubRulesVM LiveData setValue
     * VERIFIED from sources: com.smzdm.client.android.module.guanzhu.subrules.FollowSubRulesVM
     * This is where the API response data flows through after parsing
     */
    private void hookFollowSubRulesVM(ClassLoader classLoader) {
        try {
            Class<?> followSubRulesVMClass = XposedHelpers.findClass(
                "com.smzdm.client.android.module.guanzhu.subrules.FollowSubRulesVM",
                classLoader
            );
            
            // Hook the MutableLiveData setValue for feed list updates
            Class<?> mutableLiveDataClass = XposedHelpers.findClass(
                "androidx.lifecycle.MutableLiveData",
                classLoader
            );
            
            // Find all MutableLiveData fields in ViewModel
            java.lang.reflect.Field[] fields = followSubRulesVMClass.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (field.getType().equals(mutableLiveDataClass)) {
                    Logger.debug("Found MutableLiveData field: " + field.getName());
                }
            }
            
            // Hook setValue method to intercept data updates
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
                            
                            // Check if it's ga.a class (feed list update wrapper)
                            if (className.equals("ga.a")) {
                                try {
                                    // Get the rows list from ga.a
                                    java.lang.reflect.Field rowsField = value.getClass().getDeclaredField("b");
                                    rowsField.setAccessible(true);
                                    Object rowsObj = rowsField.get(value);
                                    
                                    if (rowsObj instanceof List) {
                                        List<?> rows = (List<?>) rowsObj;
                                        if (!rows.isEmpty()) {
                                            Logger.debug("Intercepting ViewModel LiveData update with " + rows.size() + " items");
                                            ArticleFilter.filterArticleList(rows, false);
                                        }
                                    }
                                } catch (Exception e) {
                                    Logger.debug("Failed to filter LiveData value: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            );
            
            Logger.info("Successfully hooked FollowSubRulesVM LiveData");
        } catch (Exception e) {
            Logger.error("Failed to hook FollowSubRulesVM (non-critical)", e);
        }
    }
    
    /**
     * Strategy 2: Hook Repository response directly
     * VERIFIED from sources: ea.d class handles API responses
     * Hook the ResponseResult after JSON parsing
     */
    private void hookRepositoryResponse(ClassLoader classLoader) {
        try {
            Class<?> responseResultClass = XposedHelpers.findClass(
                "com.smzdm.client.base.coroutines.http.ResponseResult",
                classLoader
            );
            
            // Hook setData method to intercept when data is set
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
                            
                            // Check if it's FollowFeedListBean
                            if (className.contains("FollowFeedListBean")) {
                                try {
                                    // Get rows field
                                    java.lang.reflect.Field rowsField = data.getClass().getDeclaredField("rows");
                                    rowsField.setAccessible(true);
                                    Object rowsObj = rowsField.get(data);
                                    
                                    if (rowsObj instanceof List) {
                                        List<?> rows = (List<?>) rowsObj;
                                        if (!rows.isEmpty()) {
                                            Logger.debug("Intercepting Repository response with " + rows.size() + " items");
                                            ArticleFilter.filterArticleList(rows, false);
                                        }
                                    }
                                } catch (Exception e) {
                                    Logger.debug("Failed to filter Repository response: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            );
            
            Logger.info("Successfully hooked Repository ResponseResult");
        } catch (Exception e) {
            Logger.error("Failed to hook Repository (non-critical)", e);
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
