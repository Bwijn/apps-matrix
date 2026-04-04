package de.robv.android.xposed.callbacks;

public abstract class XCallback {
    public static final int PRIORITY_DEFAULT = 50;
    public static final int PRIORITY_HIGHEST = 10000;
    public static final int PRIORITY_LOWEST = -10000;

    public XCallback() {}
    public XCallback(int priority) {}
}
