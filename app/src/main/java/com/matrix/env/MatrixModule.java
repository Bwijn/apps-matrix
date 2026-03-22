package com.matrix.env;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MatrixModule implements IXposedHookLoadPackage {

    private static final String TAG = "AppsMatrix";
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + ": handleLoadPackage called for " + lpparam.packageName);
        // Load config
        JSONObject matrix = loadMatrixFor(lpparam.packageName);
        if (matrix == null) {
            XposedBridge.log(TAG + ": no matrix for " + lpparam.packageName + ", skipping");
            return;
        }

        XposedBridge.log(TAG + ": hooking " + lpparam.packageName);

        // Hook TelephonyManager in this app's process
        hookMethod(lpparam, "getSimOperator", matrix, "sim_operator");
        hookMethod(lpparam, "getSimOperatorName", matrix, "sim_operator_name");
        hookMethod(lpparam, "getSimCountryIso", matrix, "sim_country");
        hookMethod(lpparam, "getNetworkOperator", matrix, "network_operator");
        hookMethod(lpparam, "getNetworkOperatorName", matrix, "network_operator_name");
        hookMethod(lpparam, "getNetworkCountryIso", matrix, "network_country");

        // Hook Locale
        hookLocale(lpparam, matrix);

        // Hook TimeZone
        hookTimeZone(lpparam, matrix);

        XposedBridge.log(TAG + ": all hooks installed for " + lpparam.packageName);
    }

    private JSONObject loadMatrixFor(String packageName) {
        try {
            // Read from module's own APK assets via classloader
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("assets/matrix.json");
            if (is == null) {
                XposedBridge.log(TAG + ": matrix.json not found in classloader resources");
                return null;
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[1024];
            int len;
            while ((len = is.read(tmp)) != -1) {
                baos.write(tmp, 0, len);
            }
            is.close();
            byte[] buf = baos.toByteArray();

            JSONObject root = new JSONObject(new String(buf, "UTF-8"));
            if (root.has(packageName)) {
                return root.getJSONObject(packageName);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": config error: " + t.getMessage());
        }
        return null;
    }

    private void hookMethod(XC_LoadPackage.LoadPackageParam lpparam,
                            final String methodName, final JSONObject matrix, final String configKey) {
        // Hook no-arg overload
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager",
                lpparam.classLoader,
                methodName,
                new XC_MethodHook() {
                    @Override
                    public void afterHookedMethod(MethodHookParam param) {
                        try {
                            String val = matrix.getString(configKey);
                            param.setResult(val);
                            XposedBridge.log(TAG + ": " + methodName + " -> " + val);
                        } catch (Throwable ignored) {}
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook fail " + methodName + ": " + t.getMessage());
        }

        // Hook int-subscriptionId overload
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager",
                lpparam.classLoader,
                methodName,
                int.class,
                new XC_MethodHook() {
                    @Override
                    public void afterHookedMethod(MethodHookParam param) {
                        try {
                            String val = matrix.getString(configKey);
                            param.setResult(val);
                        } catch (Throwable ignored) {}
                    }
                }
            );
        } catch (Throwable ignored) {}
    }

    private void hookLocale(XC_LoadPackage.LoadPackageParam lpparam, final JSONObject matrix) {
        try {
            final String lang = matrix.getString("locale_language");
            final String country = matrix.getString("locale_country");
            final Locale spoofed = new Locale(lang, country);

            XposedHelpers.findAndHookMethod(
                "java.util.Locale",
                lpparam.classLoader,
                "getDefault",
                new XC_MethodHook() {
                    @Override
                    public void afterHookedMethod(MethodHookParam param) {
                        param.setResult(spoofed);
                    }
                }
            );
            XposedBridge.log(TAG + ": hooked Locale -> " + spoofed);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": locale hook fail: " + t.getMessage());
        }
    }

    private void hookTimeZone(XC_LoadPackage.LoadPackageParam lpparam, final JSONObject matrix) {
        try {
            final String tz = matrix.getString("timezone");
            final TimeZone spoofedTz = TimeZone.getTimeZone(tz);

            XposedHelpers.findAndHookMethod(
                "java.util.TimeZone",
                lpparam.classLoader,
                "getDefault",
                new XC_MethodHook() {
                    @Override
                    public void afterHookedMethod(MethodHookParam param) {
                        param.setResult(spoofedTz);
                    }
                }
            );
            XposedBridge.log(TAG + ": hooked TimeZone -> " + tz);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": timezone hook fail: " + t.getMessage());
        }
    }
}
