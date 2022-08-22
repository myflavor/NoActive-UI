package cn.myflv.noactive.core.entity;

public interface MethodEnum {
    String updateActivityUsageStats = "updateActivityUsageStats";
    String deliverToRegisteredReceiverLocked = "deliverToRegisteredReceiverLocked";
    String appNotResponding = "appNotResponding";
    String startAnrConsumerIfNeeded = "startAnrConsumerIfNeeded";
    String isAppForeground = "isAppForeground";
    String add = "add";
    String killServicesLocked = "killServicesLocked";
    String setCurAdj = "setCurAdj";
    String applyOomAdjLocked = "applyOomAdjLocked";
    String getEnable = "getEnable";
    String isFreezerSupported = "isFreezerSupported";
    String setProcessFrozen = "setProcessFrozen";
    String useFreezer = "useFreezer";
    String computeOomAdjLSP = "computeOomAdjLSP";
    String setOomAdj = "setOomAdj";
    String clearAppWhenScreenOffTimeOutInNight = "clearAppWhenScreenOffTimeOutInNight";
    String clearAppWhenScreenOffTimeOut = "clearAppWhenScreenOffTimeOut";
    String clearUnactiveApps = "clearUnactiveApps";
    String startActivityWithFeature = "startActivityWithFeature";
    String clearApp = "clearApp";
    String makePackageIdle = "makePackageIdle";
    String uidIdle = "uidIdle";
    String getService = "getService";
    String releaseWakeLockInternal = "releaseWakeLockInternal";
    String onStart = "onStart";
    String isKilled = "isKilled";
    String setKilled = "setKilled";
    String forceStopPackage = "forceStopPackage";
    String kill = "kill";
    String activityIdleInternal = "activityIdleInternal";
}
