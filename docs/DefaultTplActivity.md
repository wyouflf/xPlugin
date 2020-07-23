# Activity的使用

#### 1. 在插件的Manifest中注册Activity:

注意: 这种方式注册的Activity不能被外部应用打开, 需要直接被外部应用打开的需要在宿主中注册.

注册示例:
```xml
<activity android:theme="myTheme" android:name=".TestActivity"/>

<!-- 或 -->
<activity android:theme="myTheme" android:name=".TestActivity">
    <intent-filter>
        <action android:name="action.xxx.xxx" />
    </intent-filter>
</activity>
```

#### 3. 打开插件中的 Activity:


```java
Intent intent = new Intent(TestActivity.class);
// intent.setPackage("org.xplugin.demo.main"); // 指定类具体打开的类时, 插件的包名可省略.
startActivity(intent);
```

或

```java
Intent intent = new Intent("action.xxx.xxx");
intent.setPackage("org.xplugin.demo.main");
startActivity(intent);
```


