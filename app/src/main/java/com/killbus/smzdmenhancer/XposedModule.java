package com.killbus.smzdmenhancer;

import com.killbus.smzdmenhancer.utils.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Main Xposed module entry point
 * Direct response String hooking strategy - simplest and most reliable
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
     * Initialize hooks - Direct response String hooking
     */
    private void initializeHooks(ClassLoader classLoader) {
        try {
            // Hook the network callback interface bm.e<String>.onSuccess(String)
            // This is called with the raw JSON response before parsing
            hookNetworkCallback(classLoader);
            
            Logger.info("All hooks initialized successfully");
            
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
     * Hook FollowSubRulesVM.requestFeedList callback
     * VERIFIED from sources: 
     * - FollowSubRulesVM.java line 64: private final String f20026f = "https://dingyue-api.smzdm.com/home/list"
     * - This ViewModel specifically handles the follow feed list
     */
    private void hookNetworkCallback(ClassLoader classLoader) {
        try {
            // Hook the specific ViewModel's inner callback class
            // From FollowSubRulesVM$a$a$a (the callback for /home/list request)
            Class<?> followSubRulesVMClass = XposedHelpers.findClass(
                "com.smzdm.client.android.module.guanzhu.subrules.FollowSubRulesVM",
                classLoader
            );
            
            // Find the inner class that implements bm.e
            // Pattern: FollowSubRulesVM$a$a$a implements bm.e<String>
            Class<?>[] declaredClasses = followSubRulesVMClass.getDeclaredClasses();
            for (Class<?> innerClass : declaredClasses) {
                // Check if this class implements bm.e
                Class<?>[] interfaces = innerClass.getInterfaces();
                boolean implementsCallback = false;
                for (Class<?> iface : interfaces) {
                    if (iface.getName().equals("bm.e")) {
                        implementsCallback = true;
                        break;
                    }
                }
                
                if (implementsCallback) {
                    Logger.info("Found callback class: " + innerClass.getName());
                    
                    // Hook onSuccess method of this specific callback
                    XposedHelpers.findAndHookMethod(
                        innerClass,
                        "onSuccess",
                        Object.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Object response = param.args[0];
                                
                                if (response instanceof String) {
                                    String jsonStr = (String) response;
                                    Logger.info("*** Intercepted /home/list response, length: " + jsonStr.length());
                                    
                                    try {
                                        String filteredJson = filterJsonResponse(jsonStr);
                                        if (filteredJson != null && !filteredJson.equals(jsonStr)) {
                                            param.args[0] = filteredJson;
                                            Logger.info("*** Response filtered successfully");
                                        }
                                    } catch (Exception e) {
                                        Logger.error("Error filtering response", e);
                                    }
                                }
                            }
                        }
                    );
                    Logger.info("Successfully hooked callback: " + innerClass.getName());
                }
            }
            
        } catch (Exception e) {
            Logger.error("Failed to hook FollowSubRulesVM callback", e);
        }
    }
    
    /**
     * Filter JSON response string
     * Removes articles that don't meet the criteria
     */
    private String filterJsonResponse(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            
            // Check if this has the expected structure
            if (!root.has("data")) {
                return null;
            }
            
            JSONObject data = root.getJSONObject("data");
            if (!data.has("rows")) {
                return null;
            }
            
            JSONArray rows = data.getJSONArray("rows");
            JSONArray filteredRows = new JSONArray();
            
            int totalDropped = 0;
            
            for (int i = 0; i < rows.length(); i++) {
                JSONObject article = rows.getJSONObject(i);
                
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
                
                // Filter by comment threshold
                if (commentCount >= Config.COMMENT_THRESHOLD) {
                    filteredRows.put(article);
                } else {
                    String title = article.optString("article_title", "Unknown");
                    String id = article.optString("article_id", "N/A");
                    Logger.logDroppedArticle(title, id, "JSONFilter");
                    totalDropped++;
                }
            }
            
            if (totalDropped > 0) {
                Logger.info(String.format("Filtered JSON: %d dropped, %d kept", 
                    totalDropped, filteredRows.length()));
                
                // Update the rows array
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
     * Show Toast notification in the target app
     */
    private void showToast(final ClassLoader classLoader, final String message) {
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader);
            Object currentActivityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
            Object context = XposedHelpers.callMethod(currentActivityThread, "getApplication");
            
            final Object appContext = context;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Class<?> looperClass = XposedHelpers.findClass("android.os.Looper", classLoader);
                        Object mainLooper = XposedHelpers.callStaticMethod(looperClass, "getMainLooper");
                        
                        Class<?> handlerClass = XposedHelpers.findClass("android.os.Handler", classLoader);
                        Object handler = XposedHelpers.newInstance(handlerClass, mainLooper);
                        
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
                                        0
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
