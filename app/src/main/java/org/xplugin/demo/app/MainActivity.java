package org.xplugin.demo.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.xplugin.core.msg.MsgCallback;
import org.xplugin.core.msg.PluginMsg;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        showPermissions();

        PluginMsg msg = new PluginMsg("getView");
        msg.setTargetPackage("org.xplugin.demo.main");
        msg.send(new MsgCallback() {
            @Override
            public void onSuccess(PluginMsg result) {
                View testView = (View) result.getOutParam("testView");
                LinearLayout layout = findViewById(R.id.ll_test);
                layout.addView(testView);
            }

            @Override
            public void onError(Throwable ex, boolean isOnCallback) {

            }
        });
    }

    public void onBtn1Click(View view) {
        Intent bcIntent = new Intent("com.demo.receiver");
        this.sendBroadcast(bcIntent);

        Intent intent = new Intent("xplugin.Main");
        intent.setPackage("org.xplugin.demo.main");
        //intent.putExtra(ActivityHook.PAGE_TEMPLATE_KEY, "MyCustomTplActivity");
        startActivity(intent);
    }

    private static final int PERMISSION_REQ_CODE = 100;

    //请求权限
    public void showPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.INSTALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.INTERNET,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.INSTALL_PACKAGES
            }, PERMISSION_REQ_CODE);
        } else {
            // PERMISSION_GRANTED
        }
    }

    //Android6.0申请权限的回调方法
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQ_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // PERMISSION_GRANTED
                }
                break;
            default:
                break;
        }
    }
}
