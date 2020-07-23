package org.xplugin.demo.module2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.ViewGroup;

import org.xutils.common.util.LogUtil;

public class SpringView extends ViewGroup {

    public SpringView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SpringView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @SuppressLint("NewApi")
    public SpringView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.XtSpringView);
        String test = array.getString(R.styleable.XtSpringView_header);
        LogUtil.e("############: obtainStyledAttributes: " + test);
    }

    /*public static int[] getAttrs(Context context, String name) {
        try {
            Field[] fields = Class.forName(context.getPackageName() + ".R$styleable").getFields();
            for (Field field : fields) {
                if (field.getName().equals(name)) {
                    return (int[]) field.get(null);
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
        return null;
    }*/

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

    }
}
