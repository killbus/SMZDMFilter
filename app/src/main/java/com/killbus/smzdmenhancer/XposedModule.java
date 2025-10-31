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
     * Using OkHttp Response interception - the simplest and most reliable approach
     */
    private void initializeHooks(ClassLoader classLoader) {
        try {
            // Primary Strategy: Hook OkHttp Response body (intercepts raw JSON)
            hookOkHttpResponse(classLoader);
            
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
     * Hook OkHttp ResponseBody to intercept and modify raw JSON response
     * This is the simplest and most reliable approach:
     * - Works at network layer before any parsing
     * - No need to track obfuscated class names
     * - Directly modifies the JSON string
     */
    private void hookOkHttpResponse(ClassLoader classLoader) {
        try {
            // Hook okhttp3.ResponseBody.string() method
            Class<?> responseBodyClass = XposedHelpers.findClass("okhttp3.ResponseBody", classLoader);
            
            XposedHelpers.findAndHookMethod(
                responseBodyClass,
                "string",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String originalResponse = (String) param.getResult();
                        
                        // Only process responses from our target API
                        if (originalResponse != null && originalResponse.contains("\"rows\"")) {
                            // Check if it's a follow feed response
                            if (originalResponse.contains("\"article_id\"") || 
                                originalResponse.contains("\"article_title\"")) {
                                
                                Logger.info("Intercepting API response, processing JSON...");
                                
                                try {
                                    // Parse and filter the JSON
                                    String modifiedResponse = filterJsonResponse(originalResponse);
                                    
                                    // Replace the response
                                    param.setResult(modifiedResponse);
                                    
                                    Logger.info("JSON response filtered successfully");
                                } catch (Exception e) {
                                    Logger.error("Failed to filter JSON response: " + e.getMessage(), e);
                                    // Keep original response on error
                                }
                            }
                        }
                    }
                }
            );
            
            Logger.info("Successfully hooked OkHttp ResponseBody.string()");
        } catch (Exception e) {
            Logger.error("Failed to hook OkHttp ResponseBody", e);
        }
    }
    
    /**
     * Filter JSON response by removing articles that don't meet criteria
     * Uses simple string manipulation to avoid heavy JSON parsing
     */
    private String filterJsonResponse(String jsonResponse) {
        try {
            // Use org.json which is available in Android
            org.json.JSONObject jsonObj = new org.json.JSONObject(jsonResponse);
            
            // Check if it has data.rows structure
            if (!jsonObj.has("data")) {
                return jsonResponse;
            }
            
            org.json.JSONObject data = jsonObj.getJSONObject("data");
            if (!data.has("rows")) {
                return jsonResponse;
            }
            
            org.json.JSONArray rows = data.getJSONArray("rows");
            Logger.debug("Found " + rows.length() + " rows in response");
            
            // Filter rows
            org.json.JSONArray filteredRows = new org.json.JSONArray();
            int droppedCount = 0;
            
            for (int i = 0; i < rows.length(); i++) {
                org.json.JSONObject row = rows.getJSONObject(i);
                
                // Check article_comment field
                int commentCount = 0;
                if (row.has("article_comment")) {
                    String commentStr = row.optString("article_comment", "0");
                    try {
                        commentCount = Integer.parseInt(commentStr);
                    } catch (NumberFormatException e) {
                        // Keep if can't parse
                        commentCount = Config.COMMENT_THRESHOLD;
                    }
                }
                
                // Also check article_interaction.article_comment
                if (row.has("article_interaction")) {
                    org.json.JSONObject interaction = row.getJSONObject("article_interaction");
                    if (interaction.has("article_comment")) {
                        String commentStr = interaction.optString("article_comment", "0");
                        try {
                            commentCount = Integer.parseInt(commentStr);
                        } catch (NumberFormatException e) {
                            commentCount = Config.COMMENT_THRESHOLD;
                        }
                    }
                }
                
                // Filter logic
                if (commentCount >= Config.COMMENT_THRESHOLD) {
                    filteredRows.put(row);
                } else {
                    String title = row.optString("article_title", "Unknown");
                    String id = row.optString("article_id", "N/A");
                    Logger.logDroppedArticle(title, id, "OkHttp");
                    droppedCount++;
                }
            }
            
            if (droppedCount > 0) {
                Logger.info(String.format("Filtered response: %d dropped, %d remaining", 
                    droppedCount, filteredRows.length()));
                
                // Replace rows with filtered version
                data.put("rows", filteredRows);
                jsonObj.put("data", data);
                
                return jsonObj.toString();
            }
            
            return jsonResponse;
            
        } catch (Exception e) {
            Logger.error("Error in filterJsonResponse: " + e.getMessage(), e);
            return jsonResponse; // Return original on error
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
