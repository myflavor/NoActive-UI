package cn.myflv.noactive.core.hook;

import cn.myflv.noactive.constant.ClassConstants;
import cn.myflv.noactive.constant.FieldConstants;
import cn.myflv.noactive.constant.MethodConstants;
import cn.myflv.noactive.core.entity.MemData;
import cn.myflv.noactive.core.hook.base.AbstractMethodHook;
import cn.myflv.noactive.core.hook.base.MethodHook;
import cn.myflv.noactive.core.server.BroadcastFilter;
import cn.myflv.noactive.core.server.ProcessRecord;
import cn.myflv.noactive.core.server.ReceiverList;
import cn.myflv.noactive.core.utils.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 广播分发Hook.
 */
public class BroadcastDeliverHook extends MethodHook {
    /**
     * 内存数据.
     */
    private final MemData memData;

    public BroadcastDeliverHook(ClassLoader classLoader, MemData memData) {
        super(classLoader);
        this.memData = memData;
    }

    @Override
    public String getTargetClass() {
        return ClassConstants.BroadcastQueue;
    }

    @Override
    public String getTargetMethod() {
        return MethodConstants.deliverToRegisteredReceiverLocked;
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{ClassConstants.BroadcastRecord, ClassConstants.BroadcastFilter, boolean.class, int.class};
    }

    @Override
    public XC_MethodHook getTargetHook() {
        return new AbstractMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) throws Throwable {
                Object[] args = param.args;
                if (args[1] == null) {
                    return;
                }
                BroadcastFilter broadcastFilter = new BroadcastFilter(args[1]);
                ReceiverList receiverList = broadcastFilter.getReceiverList();
                // 如果广播为空就不处理
                if (receiverList == null) {
                    return;
                }
                ProcessRecord processRecord = receiverList.getProcessRecord();
                // 如果进程或者应用信息为空就不处理
                if (processRecord == null) {
                    return;
                }

                String packageName = processRecord.getPackageName();
                broadcastStart(param, processRecord.getUserId(), packageName);
                if (!memData.isProcessFreezer(processRecord.getUserId(), processRecord)) {
                    return;
                }

                // 暂存
                Object app = processRecord.getProcessRecord();
                param.setObjectExtra(FieldConstants.app, app);
                Log.d(processRecord.getProcessName() + " clear broadcast");
                // 清楚广播
                receiverList.clear();
            }

            @Override
            protected void afterMethod(MethodHookParam param) throws Throwable {
                // 恢复被修改的参数
                restore(param);
                // 广播结束
                broadcastFinish(param);
            }
        };
    }

    /**
     * 广播开始执行
     *
     * @param packageName 包名
     */
    private void broadcastStart(XC_MethodHook.MethodHookParam param, int userId, String packageName) {
        memData.setBroadcastApp(userId, packageName);
        param.setObjectExtra(FieldConstants.userId, userId);
        param.setObjectExtra(FieldConstants.packageName, packageName);
    }

    /**
     * 广播结束执行
     */
    private void broadcastFinish(XC_MethodHook.MethodHookParam param) {
        Object obj = param.getObjectExtra(FieldConstants.userId);
        if (obj == null) {
            return;
        }
        int userId = (int) obj;
        memData.removeBroadcastApp(userId);
    }

    /**
     * 恢复被修改的参数
     */
    private void restore(XC_MethodHook.MethodHookParam param) {
        // 获取进程
        Object app = param.getObjectExtra(FieldConstants.app);
        if (app == null) {
            return;
        }

        Object[] args = param.args;
        if (args[1] == null) {
            return;
        }
        Object receiverList = XposedHelpers.getObjectField(args[1], FieldConstants.receiverList);
        if (receiverList == null) {
            return;
        }
        // 还原修改
        XposedHelpers.setObjectField(receiverList, FieldConstants.app, app);
    }

    @Override
    public int getMinVersion() {
        return ANY_VERSION;
    }

    @Override
    public String successLog() {
        return "Listen broadcast deliver";
    }

}
