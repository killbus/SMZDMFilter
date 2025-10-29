package com.smzdm.filter;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class HookMain implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // change target package if needed
        if (!lpparam.packageName.equals("com.smzdm.client.android")) return;

        XposedBridge.log("✅ SMZDMFilter injected into: " + lpparam.packageName);

        try {
            Class<?> clazz = XposedHelpers.findClass(
                "com.smzdm.client.android.module.guanzhu.subrules.FollowSubRulesVM",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                clazz,
                "f", // obfuscated name in some builds; original requestFeedList may be renamed - see below
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("➡️ FollowSubRulesVM.f called, args: " + java.util.Arrays.toString(param.args));
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("❌ Hook error: " + t);
        }
    }
}
