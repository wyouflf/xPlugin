package org.xplugin.demo.main;

import android.app.LocalActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.xplugin.core.msg.MsgCallback;
import org.xplugin.core.msg.PluginMsg;
import org.xutils.common.util.LogUtil;


public class MainActivity extends AppCompatActivity {

    LocalActivityManager activityManager = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        activityManager = new LocalActivityManager(this, true);
        activityManager.dispatchCreate(savedInstanceState);
        Intent intent = new Intent(this, ThirdPartyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        LinearLayout ll = findViewById(R.id.ll_test);
        ll.addView(activityManager.startActivity("ModuleMain", intent).getDecorView());

        Intent service = new Intent(this, DemoService.class);
        ResolveInfo resolveInfo = this.getPackageManager().resolveService(service, PackageManager.MATCH_DEFAULT_ONLY);
        LogUtil.d("ResolveInfo: " + resolveInfo.serviceInfo.toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityManager.dispatchPause(this.isFinishing());
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityManager.dispatchResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        activityManager.dispatchStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activityManager.dispatchDestroy(this.isFinishing());
    }

    public void btn1OnClick(View view) {
        Intent intent = new Intent("xplugin.Main");
        intent.setPackage("org.xplugin.demo.module1");
        startActivity(intent);
        this.overridePendingTransition(R.anim.xt_activity_open, R.anim.xt_activity_close_right);
    }

    public void btn2OnClick(View view) {
        Intent intent = new Intent("xplugin.Main");
        intent.setPackage("org.xplugin.demo.module2");
        startActivity(intent);
    }

    public void btn3OnClick(View view) {
        PluginMsg msg = new PluginMsg("sayHi");
        msg.setTargetPackage("org.xplugin.demo.module1");
        msg.send(new MsgCallback() {
            @Override
            public void onSuccess(PluginMsg result) {
                Toast.makeText(MainActivity.this, String.valueOf(result.getOutParam("content")), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(Throwable ex, boolean isOnCallback) {
                Toast.makeText(MainActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

    }

    public void btn4OnClick(View view) {
        Intent intent = new Intent(this, DemoActivity.class);
        startActivity(intent);
    }

    public void btn5OnClick(View view) {
        Intent intent = new Intent(this, ThirdPartyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        LinearLayout ll = findViewById(R.id.ll_test);
        ll.addView(activityManager.startActivity("ModuleMain", intent).getDecorView());
    }
}
