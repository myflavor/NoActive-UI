package cn.myflv.noactive.utils;

import android.util.Log;

import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cn.myflv.noactive.core.utils.FreezerConfig;

public class ConfigUtils {

    private final static String TAG = "NoActive";


    public static void delIfExist(String name, String str) {
        Set<String> lineSet = get(name);
        if (!lineSet.contains(str)) {
            return;
        }
        lineSet.remove(str);
        write(name, lineSet);
    }

    public static void addIfNot(String name, String str) {
        Set<String> lineSet = get(name);
        if (lineSet.contains(str)) {
            return;
        }
        lineSet.add(str);
        write(name, lineSet);
    }

    public static void write(String name, Collection<String> lines) {
        try {
            SuFile suFile = new SuFile(FreezerConfig.ConfigDir, name);
            OutputStream open = SuFileOutputStream.open(suFile);
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(open), 1024);
            for (String line : lines) {
                bufferedWriter.write(line + "\n");
            }
            bufferedWriter.flush();
            bufferedWriter.close();
        } catch (IOException e) {
            Log.e(TAG, name + " file write filed");
        }
    }


    public static Set<String> get(String name) {
        Set<String> set = new LinkedHashSet<>();
        try {

            SuFile suFile = new SuFile(FreezerConfig.ConfigDir, name);
            InputStream open = SuFileInputStream.open(suFile);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(open));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String finalLine = line.trim();
                if ("".equals(finalLine)) continue;
                set.add(finalLine);
            }
            bufferedReader.close();
        } catch (FileNotFoundException fileNotFoundException) {
            Log.e(TAG, name + " file not found: " + fileNotFoundException.getMessage());
        } catch (IOException ioException) {
            Log.e(TAG, name + " file read filed");
        }
        return set;
    }
}
