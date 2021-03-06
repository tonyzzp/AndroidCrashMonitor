package me.izzp.crashmonitor.demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import me.izzp.crashmonitor.CrashMonitor;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashMonitor.Config cfg = new CrashMonitor.Config();
        cfg.email = "myemail@qq.com";
        CrashMonitor.init(this, cfg);
        CrashMonitor.setLogProvider(new CrashMonitor.LogProvider() {
            @Override
            public String provide() {
                return "mylog:中文:" + this.getClass().toString();
            }
        });

        if (CrashMonitor.hasCrashLogs() && CrashMonitor.shouldSendLog()) {
            CrashMonitor.showReportDialog(this, null);
        }
        CrashMonitor.showReportDialog(this, new CrashMonitor.Feedback() {
            @Override
            public void onSend() {

            }

            @Override
            public void onDonot() {

            }

            @Override
            public void onNever() {

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "crash").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                int i = 0 / 0;
                System.out.println(i);
                break;
        }
        return true;
    }
}
