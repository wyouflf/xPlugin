package org.xplugin.demo.main;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.xutils.common.util.LogUtil;

import java.io.File;

public class DemoActivity extends AppCompatActivity {

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LogUtil.d("###################");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LogUtil.d("###################");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        Intent service = new Intent(this, DemoService.class);
        this.startService(service);
        this.bindService(service, connection, Context.BIND_AUTO_CREATE);
    }

    public void btnProviderOnClick(View view) {
        this.unbindService(connection);

        File file = new File(Environment.getExternalStorageDirectory() + "/aaa/111.apk");

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = FileProvider.getUriForFile(this, this.getPackageName() + ".fileProvider", file);
            LogUtil.e("##### 1 " + uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(file);
            LogUtil.e("##### 2 " + uri);
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        this.startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent service = new Intent(this, DemoService.class);
        this.stopService(service);
    }
}