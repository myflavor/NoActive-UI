package cn.myflv.noactive.core.handler;

import android.content.pm.ApplicationInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.myflv.noactive.constant.ClassConstants;
import cn.myflv.noactive.constant.MethodConstants;
import cn.myflv.noactive.core.entity.MemData;
import cn.myflv.noactive.core.server.ActivityManagerService;
import cn.myflv.noactive.core.server.ProcessRecord;
import cn.myflv.noactive.core.utils.FreezeUtils;
import cn.myflv.noactive.core.utils.FreezerConfig;
import cn.myflv.noactive.core.utils.Log;
import cn.myflv.noactive.core.utils.ThreadUtils;
import de.robv.android.xposed.XposedHelpers;

public class FreezerHandler {
    /**
     * Binder休眠.
     */
    private final static int BINDER_IDLE = 0;
    private final ClassLoader classLoader;
    private final MemData memData;
    private final FreezeUtils freezeUtils;

    public FreezerHandler(ClassLoader classLoader, MemData memData, FreezeUtils freezeUtils) {
        this.classLoader = classLoader;
        this.memData = memData;
        this.freezeUtils = freezeUtils;
        if (FreezerConfig.isConfigOn(FreezerConfig.IntervalUnfreeze)) {
            enableIntervalUnfreeze();
        }
        if (FreezerConfig.isConfigOn(FreezerConfig.IntervalFreeze)) {
            enableIntervalFreeze();
        }
    }

    /**
     * 开启定时冻结
     */
    public void enableIntervalFreeze() {
        ThreadUtils.scheduleInterval(() -> {
            synchronized (memData.getUserFreezerAppMap()) {
                Set<String> freezerAppSet = memData.getUserFreezerAppMap().get(ActivityManagerService.MAIN_USER);
                if (freezerAppSet == null) {
                    return;
                }
                // 获取包名分组进程
                Map<String, List<ProcessRecord>> processMap = memData.getActivityManagerService().getProcessList().getProcessMap();
                // 遍历被冻结的APP
                for (String packageName : freezerAppSet) {
                    ThreadUtils.runWithLock(packageName, () -> {
                        // 获取应用进程
                        List<ProcessRecord> processRecords = processMap.get(packageName);
                        if (processRecords == null) {
                            return;
                        }
                        // 冻结
                        for (ProcessRecord processRecord : processRecords) {
                            if (memData.isTargetProcess(true, ActivityManagerService.MAIN_USER, processRecord)) {
                                freezeUtils.freezer(processRecord);
                            }
                        }
                    });
                }
            }
            // 如果没有APP被冻结就不处理了

        }, 1);
        Log.i("Interval freeze");
    }

    /**
     * 定时轮番解冻
     */
    public void enableIntervalUnfreeze() {
        ThreadUtils.scheduleInterval(() -> {
            synchronized (memData.getUserFreezerAppMap()) {

                Set<String> freezerAppSet = memData.getUserFreezerAppMap().get(ActivityManagerService.MAIN_USER);
                if (freezerAppSet == null) {
                    return;
                }
                // 遍历被冻结的进程
                for (String packageName : freezerAppSet) {
                    Log.d(packageName + " interval unfreeze start");
                    // 解冻
                    onResume(true, packageName, ActivityManagerService.MAIN_USER, () -> {
                        // 冻结
                        onPause(true, packageName, 3000, ActivityManagerService.MAIN_USER, () -> {
                            Log.d(packageName + " interval unfreeze finish");
                        });
                    });
                    // 结束循环
                    // 相当于只解冻没有最久没有打开的 APP
                    break;
                }
            }
        }, 1);
        Log.i("Interval unfreeze");
    }


    public void onResume(boolean handle, String packageName, int userId) {
        onResume(handle, packageName, userId, null);
    }

    /**
     * APP切换至前台.
     *
     * @param packageName 包名
     */
    public void onResume(boolean handle, String packageName, int userId, Runnable runnable) {
        // 不处理就跳过
        if (!handle) {
            return;
        }
        ThreadUtils.thawThread(userId + ":" + packageName, () -> {
            ThreadUtils.safeRun(() -> {
                // 获取应用信息
                ApplicationInfo applicationInfo = memData.getActivityManagerService().getApplicationInfo(packageName);
                if (memData.getWhiteProcessList().contains(packageName)) {
                    return;
                }
                if (memData.getSocketApps().contains(packageName)) {
                    // 清除网络监控
                    memData.clearMonitorNet(applicationInfo);
                } else {
                    // 恢复StandBy
                    memData.getAppStandbyController().forceIdleState(packageName, false);
                }
            });
            // 获取目标进程
            List<ProcessRecord> targetProcessRecords = memData.getTargetProcessRecords(userId, packageName);
            // 解冻
            freezeUtils.unFreezer(targetProcessRecords);
            // 移除被冻结APP
            memData.setAppFreezer(userId, packageName, false);
            if (Thread.currentThread().isInterrupted()) {
                Log.d(packageName + " event updated");
                return;
            }

            if (runnable != null) {
                runnable.run();
            }
        });
    }

    public void onPause(boolean handle, String packageName, int userId, long delay) {
        onPause(handle, packageName, userId, delay, null);
    }

    /**
     * APP切换至后台.
     *
     * @param packageName 包名
     */
    public void onPause(boolean handle, String packageName, int userId, long delay, Runnable runnable) {
        // 不处理就跳过
        if (!handle) {
            return;
        }
        ThreadUtils.newThread(userId + ":" + packageName, () -> {
            // 如果是前台应用就不处理
            if (isAppForeground(packageName)) {
                Log.d(packageName + " is in foreground");
                return;
            }
            // 获取目标进程
            List<ProcessRecord> targetProcessRecords = memData.getTargetProcessRecords(userId, packageName);
            // 如果目标进程为空就不处理
            if (targetProcessRecords.isEmpty()) {
                return;
            }
            // 后台应用添加包名
            memData.setAppFreezer(userId, packageName, true);
            // 等待应用未执行广播
            boolean broadcastIdle = memData.waitBroadcastIdle(userId, packageName);
            if (!broadcastIdle) {
                return;
            }
            // 等待 Binder 休眠
            boolean binderIdle = waitBinderIdle(packageName);
            if (!binderIdle) {
                return;
            }
            ApplicationInfo applicationInfo = memData.getActivityManagerService().getApplicationInfo(packageName);
            // 存放杀死进程
            List<ProcessRecord> killProcessList = new ArrayList<>();
            // 遍历目标进程
            for (ProcessRecord targetProcessRecord : targetProcessRecords) {
                if (Thread.currentThread().isInterrupted()) {
                    Log.d(packageName + " event updated");
                    return;
                }
                // 目标进程名
                String processName = targetProcessRecord.getProcessName();
                if (memData.getKillProcessList().contains(processName)) {
                    killProcessList.add(targetProcessRecord);
                } else {
                    // 冻结
                    freezeUtils.freezer(targetProcessRecord);
                }
            }
            if (Thread.currentThread().isInterrupted()) {
                Log.d(packageName + " event updated");
                return;
            }
            ThreadUtils.safeRun(() -> {
                // 如果白名单进程不包含主进程就释放唤醒锁
                if (memData.getWhiteProcessList().contains(packageName)) {
                    return;
                }
                // 是否唤醒锁
                memData.getPowerManagerService().release(packageName);
                if (!memData.getSocketApps().contains(packageName)) {
                    memData.getAppStandbyController().forceIdleState(packageName, true);
                    memData.getNetworkManagementService().socketDestroy(applicationInfo);
                } else {
                    memData.monitorNet(applicationInfo);
                }
            });
            if (Thread.currentThread().isInterrupted()) {
                Log.d(packageName + " event updated");
                return;
            }
            ThreadUtils.safeRun(() -> {
                freezeUtils.kill(killProcessList);
            });
            if (runnable != null) {
                runnable.run();
            }
        }, delay);
    }

    /**
     * 应用是否前台.
     *
     * @param packageName 包名
     */
    public boolean isAppForeground(String packageName) {
        // 忽略前台 就代表不在后台
        if (memData.getDirectApps().contains(packageName)) {
            return memData.getActivityManagerService().isTopApp(packageName);
        }
        // 调用AMS的方法判断
        return memData.getActivityManagerService().isForegroundApp(packageName);
    }

    /**
     * 临时解冻.
     *
     * @param uid 应用ID
     */
    public void temporaryUnfreezeIfNeed(int uid, String reason) {
        if (uid < 10000) {
            return;
        }
        String packageName = memData.getActivityManagerService().getNameForUid(uid);
        if (packageName == null) {
            Log.w("uid  " + uid + "  not found");
            return;
        }
        if (!memData.isAppFreezer(ActivityManagerService.MAIN_USER, packageName)) {
            return;
        }
        Log.i(packageName + " " + reason);
        onResume(true, packageName, ActivityManagerService.MAIN_USER, () -> {
            onPause(true, packageName, ActivityManagerService.MAIN_USER, 3000);
        });
    }


    /**
     * Binder状态.
     *
     * @param uid 应用ID
     * @return [IDLE|BUSY]
     */
    public int binderState(int uid) {
        try {
            Class<?> GreezeManagerService = XposedHelpers.findClass(ClassConstants.GreezeManagerService, classLoader);
            return (int) XposedHelpers.callStaticMethod(GreezeManagerService, MethodConstants.nQueryBinder, uid);
        } catch (Throwable ignored) {
        }
        // 报错就返回已休眠，相当于这个功能不存在
        return BINDER_IDLE;
    }


    /**
     * 等待Binder休眠
     *
     * @param packageName 包名
     */
    public boolean waitBinderIdle(String packageName) {
        // 获取应用信息
        ApplicationInfo applicationInfo = memData.getActivityManagerService().getApplicationInfo(packageName);
        if (applicationInfo == null) {
            return true;
        }
        // 重试次数
        int retry = 0;
        // 3次重试，如果不进休眠就直接冻结了
        while (binderState(applicationInfo.uid) != BINDER_IDLE && retry < 3) {
            Log.w(packageName + " binder busy");
            boolean sleep = ThreadUtils.sleep(1000);
            if (!sleep) {
                Log.d(packageName + " binder idle wait canceled");
                return false;
            }
            retry++;
        }
        Log.d(packageName + " binder idle");
        return true;
    }

}
