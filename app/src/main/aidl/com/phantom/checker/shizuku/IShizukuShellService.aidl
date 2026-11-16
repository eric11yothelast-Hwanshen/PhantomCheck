package com.phantom.checker.shizuku;

interface IShizukuShellService {
    void destroy() = 16777114;
    String exec(String command) = 1;
    void startWatchdog(String packageName, String serviceClassName) = 2;
    void stopWatchdog() = 3;
    boolean isWatchdogRunning() = 4;
}
