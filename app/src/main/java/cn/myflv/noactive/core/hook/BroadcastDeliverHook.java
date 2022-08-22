package cn.myflv.noactive.core.hook;

import cn.myflv.noactive.core.entity.ClassEnum;
import cn.myflv.noactive.core.entity.FieldEnum;
import cn.myflv.noactive.core.entity.MemData;
import cn.myflv.noactive.core.entity.MethodEnum;
import cn.myflv.noactive.core.server.BroadcastFilter;
import cn.myflv.noactive.core.server.ProcessRecord;
import cn.myflv.noactive.core.server.ReceiverList;
import cn.myflv.noactive.core.utils.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class BroadcastDeliverHook extends MethodHook {
    private final MemData memData;

    public BroadcastDeliverHook(ClassLoader classLoader, MemData memData) {
        super(classLoader);
        this.memData = memData;
    }

    @Override
    public String getTargetClass() {
        return ClassEnum.BroadcastQueue;
    }

    @Override
    public String getTargetMethod() {
        return MethodEnum.deliverToRegisteredReceiverLocked;
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{ClassEnum.BroadcastRecord, ClassEnum.BroadcastFilter, boolean.class, int.class};
    }

    @Override
    public XC_MethodHook getTargetHook() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
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

                // 不是目标进程就不处理
                if (!memData.isTargetProcess(processRecord)) {
                    return;
                }

                String packageName = processRecord.getApplicationInfo().getPackageName();

                // 不是冻结APP就不处理
                if (!memData.getFreezerAppSet().contains(packageName)) {
                    return;
                }

                // 暂存
                Object app = processRecord.getProcessRecord();
                param.setObjectExtra(FieldEnum.app, app);
                Log.d(processRecord.getProcessName() + " clear broadcast");
                // 清楚广播
                receiverList.clear();
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);

                // 获取进程
                Object app = param.getObjectExtra(FieldEnum.app);
                if (app == null) {
                    return;
                }

                Object[] args = param.args;
                if (args[1] == null) {
                    return;
                }
                Object receiverList = XposedHelpers.getObjectField(args[1], FieldEnum.receiverList);
                if (receiverList == null) {
                    return;
                }
                // 还原修改
                XposedHelpers.setObjectField(receiverList, FieldEnum.app, app);
            }
        };
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
