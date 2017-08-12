package me.izzp.crashmonitor;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class CrashMonitor {

    public static class Config {
        @NonNull
        public String email;
    }

    /**
     * 请求发送反馈后用户的选择
     */
    public interface Feedback {
        /**
         * 选择了发送
         */
        void onSend();

        /**
         * 选择了不发送
         */
        void onDonot();

        /**
         * 选择了永不发送。 如果选择此项，应用自己需要记住，并且以后不再调用 {@link #showReportDialog(Activity, Feedback)} 方法
         */
        void onNever();
    }

    private static File dir;
    private static boolean hasInit;
    private static Context ctx;
    private static Config cfg;
    private static SharedPreferences sp;

    public static void init(@NonNull Context context, @NonNull Config config) {
        if (hasInit) {
            return;
        }
        hasInit = true;
        cfg = config;
        checkConfig();
        ctx = context.getApplicationContext();
        sp = ctx.getSharedPreferences("me.izzp.crashmonitor_prefs", Context.MODE_PRIVATE);
        dir = context.getExternalFilesDir("crash_logs");
        if (dir == null) {
            dir = context.getFilesDir();
            dir = new File(dir, "crash_logs");
        }
        dir.mkdirs();

        final DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    File file = new File(dir, sdf.format(new Date()) + ".log");
                    StringWriter writer = new StringWriter();
                    e.printStackTrace(new PrintWriter(writer));
                    String s = writer.toString();
                    FileWriter fw = new FileWriter(file);
                    fw.write(sysInfo());
                    fw.write(s);
                    fw.close();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                if (def != null) {
                    def.uncaughtException(t, e);
                }
            }
        });
    }

    private static void checkConfig() {
        if (cfg == null) {
            throw new RuntimeException("必须传入config");
        }
        if (TextUtils.isEmpty(cfg.email)) {
            throw new RuntimeException("config.email不能为空");
        }
    }

    private static String sysInfo() {
        StringBuilder sb = new StringBuilder();
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            sb.append("packageName:").append(pi.packageName).append("\r\n");
            sb.append("appVersionCode:").append(pi.versionCode).append("\r\n");
            sb.append("appVersionName:").append(pi.versionName).append("\r\n");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        sb.append("sysVersionCode:").append(Build.VERSION.SDK_INT).append("\r\n");
        sb.append("sysVersionName:").append(Build.VERSION.RELEASE).append("\r\n");
        sb.append("board:").append(Build.BOARD).append("\r\n");
        sb.append("model:").append(Build.MODEL).append("\r\n");
        sb.append("display:").append(Build.DISPLAY).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }

    public static boolean hasCrashLogs() {
        if (dir == null) {
            return false;
        }
        String[] fs = dir.list();
        return fs != null && fs.length > 0;
    }

    /**
     * 是否应该显示上报dialog。
     * <p>
     * 如果用户曾经在上报dialog上点击过“永不上报”，则此值会返回false
     *
     * @return
     */
    public static boolean shouldSendLog() {
        return sp.getBoolean("should_send", true);
    }

    /**
     * crash log目录。
     *
     * @return 如果目录创建失败，则返回null，并且不会记录日志
     */
    @Nullable
    public static File getLogsDir() {
        return dir;
    }

    /**
     * 显示请求上传dialog
     *
     * @param act
     * @param fb
     */
    public static void showReportDialog(@NonNull final Activity act, @Nullable final Feedback fb) {
        new AlertDialog.Builder(act)
                .setMessage("是否愿意向通过邮件我反馈程序崩溃日志？")
                .setPositiveButton("好的", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (fb != null) {
                            fb.onSend();
                        }
                        report(act);
                    }
                })
                .setNeutralButton("永不发送", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sp.edit().putBoolean("should_send", false).apply();
                        if (fb != null) {
                            fb.onNever();
                        }
                    }
                })
                .setNegativeButton("不", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (fb != null) {
                            fb.onDonot();
                        }
                    }
                })
                .show();
    }

    private static String getAppName() {
        ApplicationInfo ai = ctx.getApplicationInfo();
        CharSequence cs = ai.loadLabel(ctx.getPackageManager());
        String s = cs != null ? cs.toString() : "";
        if (s.length() == 0) {
            s = ctx.getPackageName();
        }
        return s;
    }

    private static void report(final Activity act) {
        final ProgressDialog progressDialog = new ProgressDialog(act);
        progressDialog.setMessage("正在准备日志文件");
        new Thread() {
            @Override
            public void run() {
                super.run();
                File fs[] = dir.listFiles();
                File zipfile = null;
                try {
                    zipfile = File.createTempFile("crashlogs", ".zip",
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
                    ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipfile));
                    for (File f : fs) {
                        byte[] bytes = readAll(f);
                        if (bytes != null) {
                            ZipEntry entry = new ZipEntry(f.getName());
                            entry.setSize(bytes.length);
                            out.putNextEntry(entry);
                            out.write(bytes, 0, bytes.length);
                            out.closeEntry();
                        }
                    }
                    out.flush();
                    out.close();
                    for (File f : fs) {
                        f.delete();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final File file = zipfile;
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        if (file == null) {
                            Toast.makeText(act, "准备日志文件失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                if (zipfile != null) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_EMAIL, new String[]{cfg.email});
                    intent.putExtra(Intent.EXTRA_TITLE, getName() + " 日志反馈");
                    intent.putExtra(Intent.EXTRA_SUBJECT, getName() + "  日志反馈");
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(zipfile));
                    intent.setType("message/*");
                    intent = Intent.createChooser(intent, null);
                    act.startActivity(intent);
                }
            }
        }.start();
    }

    private static byte[] readAll(File f) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            FileInputStream in = new FileInputStream(f);
            byte[] buff = new byte[1024 * 4];
            int len;
            while ((len = in.read(buff)) > 0) {
                baos.write(buff, 0, len);
            }
            in.close();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
