package cn.myflv.noactive.core.server;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.lang.reflect.Array;

import cn.myflv.noactive.core.entity.ClassEnum;
import cn.myflv.noactive.core.entity.FieldEnum;
import cn.myflv.noactive.core.entity.MethodEnum;
import cn.myflv.noactive.core.utils.Log;
import de.robv.android.xposed.XposedHelpers;
import lombok.Data;

@Data
public class NetworkManagementService {

    private final ClassLoader classLoader;

    private final Object networkManagementService;

    private final Object mNetdService;

    private final Class<?> UidRangeParcel;

    public NetworkManagementService(ClassLoader classLoader, Object networkManagementService) {
        this.classLoader = classLoader;
        this.networkManagementService = networkManagementService;
        this.mNetdService = XposedHelpers.getObjectField(networkManagementService, FieldEnum.mNetdService);
        this.UidRangeParcel = XposedHelpers.findClass(ClassEnum.UidRangeParcel, classLoader);
    }

    /**
     * 断开Socket
     *
     * @param applicationInfo 应用信息
     */
    public void socketDestroy(ApplicationInfo applicationInfo) {
        try {
            // 获取UID
            int uid = applicationInfo.uid;
            Object uidRangeParcel = makeUidRangeParcel(uid, uid);
            Object uidRangeParcels = Array.newInstance(UidRangeParcel, 1);
            Array.set(uidRangeParcels, 0, uidRangeParcel);
            XposedHelpers.callMethod(mNetdService, MethodEnum.socketDestroy, uidRangeParcels, new int[0]);
            Log.d(applicationInfo.packageName + " socket destroyed");
        } catch (Throwable throwable) {
            Log.e("socketDestroy", throwable);
        }
    }

    private Object makeUidRangeParcel(int start, int stop) {
        Object uidRangeParcel;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            uidRangeParcel = XposedHelpers.newInstance(UidRangeParcel, start, stop);
        } else {
            uidRangeParcel = XposedHelpers.newInstance(UidRangeParcel);
            XposedHelpers.setObjectField(uidRangeParcel, FieldEnum.start, start);
            XposedHelpers.setObjectField(uidRangeParcel, FieldEnum.stop, stop);
        }
        return uidRangeParcel;
    }

}