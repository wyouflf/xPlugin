package org.xplugin.core.install;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

import org.xplugin.core.PluginRuntime;
import org.xplugin.core.ctx.Host;
import org.xplugin.core.ctx.HostContextProxy;
import org.xplugin.core.ctx.Module;
import org.xplugin.core.ctx.ModuleContext;
import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.exception.PluginAlreadyLoadedException;
import org.xplugin.core.exception.PluginDisableException;
import org.xplugin.core.exception.PluginInstallException;
import org.xplugin.core.exception.PluginInstallLockException;
import org.xplugin.core.exception.PluginNeedRestartException;
import org.xutils.common.Callback;
import org.xutils.common.task.AbsTask;
import org.xutils.common.task.Priority;
import org.xutils.common.task.PriorityExecutor;
import org.xutils.common.util.IOUtil;
import org.xutils.common.util.LogUtil;
import org.xutils.common.util.ProcessLock;
import org.xutils.x;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by jiaolei on 15/6/10.
 * 插件的安装管理类
 * <p>
 * 临时解压目录:
 * $APP_DIR/xpl_modules_temp/
 * 安装路径:
 * $APP_DIR/xpl_modules/$pkg_name/$version/base.apk
 * 模块信息:
 * $APP_DIR/xpl_modules/$pkg_name/.install_info
 */
public final class Installer {

    private Installer() {
    }

    // const strings
    private final static String ASSETS_MODULE_DIR_NAME = "xpl_res";
    private final static String ASSETS_MODULE_VERSION_NAME = "assets_version";
    private final static String ASSETS_MODULE_NAME_SUFFIX = ".png";
    private final static String MODULE_INSTALL_DIR_NAME = "xpl_modules";
    private final static String MODULE_INSTALL_TEMP_DIR_NAME = "xpl_modules_temp";
    private final static String MODULE_APP_NAME = "base.apk";
    private final static String CONFIG_INSTALL_INFO_FILE_NAME = ".install_info";
    private final static String EXTERNAL_PLUGIN_DIR_NAME = "a_xpl";

    // plugins & init event
    @SuppressLint("StaticFieldLeak")
    private static Host host;
    @SuppressLint("StaticFieldLeak")
    private static Module runtimeModule;
    private static PluginInstallListener installEvent;
    private static volatile boolean initFinished = false;
    private final static HashSet<String> installedModules = new HashSet<String>(5);
    private final static ConcurrentHashMap<String, Module> loadedModules = new ConcurrentHashMap<String, Module>(5);


    // lock & thread pool
    private final static String PROCESS_LOCK_NAME = "xpl_module_install";
    private final static int PROCESS_LOCK_TIMEOUT = 1000 * 30;
    private final static Object initLock = new Object();
    private final static Executor EXECUTOR = new PriorityExecutor(1, true); // 单线程执行安装相关操作

    private final static List<String> SUPPORT_ABIs = new ArrayList<String>();

    /**
     * 加载Host及其依赖的模块.
     * PluginManager初始化时调用这个方法.
     */
    public synchronized static void initHost(final PluginInstallListener installEvent) {
        if (host == null) {

            // init SUPPORT_ABIs
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                String[] buildABIs = Build.SUPPORTED_ABIS;
                if (buildABIs != null) {
                    SUPPORT_ABIs.addAll(Arrays.asList(buildABIs));
                    // [arm64-v8a, armeabi-v7a, armeabi]
                }
            }
            if (SUPPORT_ABIs.isEmpty()) {
                SUPPORT_ABIs.add(Build.CPU_ABI); // arm64-v8a
                SUPPORT_ABIs.add(Build.CPU_ABI2); // armeabi
            }

            Installer.installEvent = installEvent;
            // init host
            Application app = x.app();
            try {
                Config config = new Config();
                config.packageName = app.getPackageName();
                host = new Host(app, config);// config的其他属性由InitTask延迟补充
                host.init();
            } catch (Throwable ex) {
                installEvent.onPluginsLoadError(
                        new PluginInstallException("init host error", ex, app.getPackageName()),
                        false);
            }

            // 1. decompress modules from assets
            // 2. install all modules from temp folder
            // 3. load runtime dependence
            // 4. load host dependence
            x.task().start(new InitTask());
        }
    }

    /**
     * 等待初始化完成, 仅提给ClassLoader使用.
     */
    public static void waitForInit() {
        if (!initFinished) {
            synchronized (initLock) {
                while (!initFinished) {
                    try {
                        initLock.wait(50);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    public static Class<?> loadClass(String className) throws ClassNotFoundException {
        Class<?> result = null;

        if (runtimeModule != null) {
            try {
                result = runtimeModule.loadClass(className);
                if (result != null) return result;
            } catch (Throwable ignored) {
            }
        }

        for (Module module : loadedModules.values()) {
            if (module != runtimeModule) {
                try {
                    result = module.loadClass(className);
                    if (result != null) return result;
                } catch (Throwable ignored) {
                }
            }
        }

        return Class.forName(className);
    }

    /**
     * 获取Host实例
     *
     * @return
     */
    public static Host getHost() {
        return host;
    }

    /**
     * 获取 Runtime Module 实例
     *
     * @return
     */
    public static Module getRuntimeModule() {
        return runtimeModule;
    }

    /**
     * 获取已加载的插件
     *
     * @param packageName
     * @return
     */
    public static Plugin getLoadedPlugin(final String packageName) {
        Plugin result = loadedModules.get(packageName);
        if (result == null && host.getConfig().getPackageName().equals(packageName)) {
            result = host;
        }
        return result;
    }

    /**
     * 获取已加载的模块
     */
    public static Map<String, Module> getLoadedModules() {
        return new HashMap<String, Module>(loadedModules);
    }

    /**
     * 获取已安装的模块packageName
     */
    public static Set<String> getInstalledModules() {
        HashSet<String> result = null;
        synchronized (installedModules) {
            result = new HashSet<String>(installedModules);
        }
        return result;
    }

    public static Plugin containsModuleActivity(String optPkg, final String className) throws Throwable {
        final Plugin[] plugins = {null};

        Set<String> installed = getInstalledModules();
        if (installed.contains(optPkg)) {
            Plugin plugin = loadedModules.get(optPkg);
            if (plugin != null && plugin.isActivityRegistered(className)) {
                return plugin;
            } else {
                x.task().startSync(new LoadTask(optPkg, new Callback.CommonCallback<Module>() {
                    @Override
                    public void onSuccess(Module result) {
                        if (result.isActivityRegistered(className)) {
                            plugins[0] = result;
                        }
                    }

                    @Override
                    public void onError(Throwable ex, boolean isOnCallback) {
                    }

                    @Override
                    public void onCancelled(CancelledException cex) {
                    }

                    @Override
                    public void onFinished() {
                    }
                }));
            }
        } else if (TextUtils.isEmpty(optPkg) || x.app().getPackageName().equals(optPkg)) {
            if (runtimeModule != null && runtimeModule.isActivityRegistered(className)) {
                return runtimeModule;
            }
            for (Module module : loadedModules.values()) {
                if (module.isActivityRegistered(className)) {
                    return module;
                }
            }
        }

        return plugins[0];
    }

    public static Plugin containsModuleService(String optPkg, final String className) throws Throwable {
        final Plugin[] plugins = {null};

        Set<String> installed = getInstalledModules();
        if (installed.contains(optPkg)) {
            Plugin plugin = loadedModules.get(optPkg);
            if (plugin != null && plugin.isServiceRegistered(className)) {
                return plugin;
            } else {
                x.task().startSync(new LoadTask(optPkg, new Callback.CommonCallback<Module>() {
                    @Override
                    public void onSuccess(Module result) {
                        if (result.isServiceRegistered(className)) {
                            plugins[0] = result;
                        }
                    }

                    @Override
                    public void onError(Throwable ex, boolean isOnCallback) {
                    }

                    @Override
                    public void onCancelled(CancelledException cex) {
                    }

                    @Override
                    public void onFinished() {
                    }
                }));
            }
        } else if (TextUtils.isEmpty(optPkg) || x.app().getPackageName().equals(optPkg)) {
            if (runtimeModule != null && runtimeModule.isServiceRegistered(className)) {
                return runtimeModule;
            }
            for (Module module : loadedModules.values()) {
                if (module.isServiceRegistered(className)) {
                    return module;
                }
            }
        }

        return plugins[0];
    }

    /**
     * 获取已安装的插件的配置信息
     *
     * @param packageNameOrPath
     * @param callback
     */
    public static void loadPluginConfig(String packageNameOrPath, Callback.CommonCallback<Config> callback) {
        x.task().start(new LoadConfigTask(packageNameOrPath, callback));
    }

    /**
     * 安装外部插件
     *
     * @param externalFile
     * @param callback
     */
    public static void installModule(final File externalFile, final Callback.CommonCallback<Module> callback) {
        x.task().start(new InstallTask(externalFile, callback));
    }

    /**
     * 卸载已安装的模块
     *
     * @param packageName
     * @param callback
     */
    public static void uninstallModule(final String packageName, final Callback.CommonCallback<Boolean> callback) {
        x.task().start(new UninstallTask(packageName, callback));
    }

    /**
     * 加载已安装插件
     *
     * @param packageName
     * @param callback
     */
    public static void loadModule(final String packageName, final Callback.CommonCallback<Module> callback) {
        x.task().start(new LoadTask(packageName, callback));
    }

    /**
     * 禁用已安装插件
     *
     * @param packageName
     * @param callback
     */
    public static void disableModule(final String packageName, final Callback.CommonCallback<Void> callback) {
        x.task().start(new ChangeStateTask(packageName, InstallInfo.State.DISABLE, callback));
    }

    /**
     * 启用已安装插件
     *
     * @param packageName
     * @param callback
     */
    public static void enableModule(final String packageName, final Callback.CommonCallback<Void> callback) {
        x.task().start(new ChangeStateTask(packageName, InstallInfo.State.ENABLE, callback));
    }

    /**
     * 获取插件的so文件
     *
     * @param packageName
     * @param libName
     * @return
     */
    public static File findLibrary(String packageName, String libName) throws IOException {
        File libDir = getInstalledModuleDir(packageName);
        return new File(libDir,
                (libName.startsWith("lib") ? libName : "lib" + libName) +
                        (libName.endsWith(".so") ? "" : ".so"));
    }

    // #########################################  私有方法和类型  ###################################################

    /**
     * 第一步:
     * 解出assets中的插件文件到临时目录
     *
     * @throws IOException
     */
    private synchronized static void decompressAssetsModules() throws IOException {

        if (!isAssetsModulesDecompressed()) { // 如果没有解压过当前版本
            AssetManager assets = x.app().getAssets();
            String[] list = assets.list(ASSETS_MODULE_DIR_NAME);
            if (list != null) {
                for (String fileName : list) {
                    if (fileName.endsWith(ASSETS_MODULE_NAME_SUFFIX)) {
                        InputStream in = null;
                        FileOutputStream out = null;
                        try {
                            in = assets.open(ASSETS_MODULE_DIR_NAME + "/" + fileName);
                            File tempFile = getTempModuleFile(fileName);
                            out = new FileOutputStream(tempFile);
                            IOUtil.copy(in, out);
                        } finally {
                            IOUtil.closeQuietly(in);
                            IOUtil.closeQuietly(out);
                        }
                    }
                }
            }

            setAssetsModulesDecompressed();
        }
    }

    /**
     * 第二步:
     * 安装临时文件到最终加载位置
     *
     * @param tempModuleFile
     * @return installed file
     * @throws IOException
     */
    private static File installTempModuleFile(File tempModuleFile) throws IOException {
        Config config = null;
        File moduleFile = null;

        // prepare config & pluginFile
        {
            config = ConfigHelper.getModuleConfig(tempModuleFile, false);
            File installDir = getModuleDir(config);
            if (installDir != null && installDir.exists()) {
                String[] files = installDir.list();
                if (files != null && files.length > 0) {
                    IOUtil.deleteFileOrDir(installDir);
                }
            }
            moduleFile = getModuleFile(config);
            File parentDir = moduleFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
        }

        // decompress so files
        HashMap<String, ZipEntry> soFiles2Extra = new HashMap<String, ZipEntry>();
        HashMap<String, Integer> soFilesAbiIndex = new HashMap<String, Integer>();
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(tempModuleFile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith("lib/")) {
                    String[] pathArray = entryName.split("/"); // ["lib", "abi", "name.so"]
                    if (pathArray.length == 3) {
                        int abiIndex = SUPPORT_ABIs.indexOf(pathArray[1]);
                        if (abiIndex >= 0) {
                            String soName = pathArray[2];
                            Integer lastABIIndex = soFilesAbiIndex.get(soName);
                            if (lastABIIndex == null || abiIndex < lastABIIndex) {
                                soFiles2Extra.put(soName, entry);
                                soFilesAbiIndex.put(soName, abiIndex);
                            }
                        }
                    }
                }
            }
            for (Map.Entry<String, ZipEntry> soEntry : soFiles2Extra.entrySet()) {
                BufferedInputStream in = null;
                BufferedOutputStream out = null;
                try {
                    in = new BufferedInputStream(zipFile.getInputStream(soEntry.getValue()));
                    File outFile = new File(moduleFile.getParent(), soEntry.getKey());
                    out = new BufferedOutputStream(new FileOutputStream(outFile));
                    IOUtil.copy(in, out);
                } finally {
                    IOUtil.closeQuietly(out);
                }
            }
        } finally {
            IOUtil.closeQuietly(zipFile);
        }

        if (moduleFile.exists()) {
            IOUtil.deleteFileOrDir(moduleFile);
        }
        if (!tempModuleFile.renameTo(moduleFile)) {
            throw new IOException("mv plugin file error: " + moduleFile.getName());
        }

        InstallInfo.writeInstallInfo(
                new InstallInfo(InstallInfo.State.ENABLE,
                        String.valueOf(config.version)),
                getInstallInfoFile(config.packageName));

        return moduleFile;
    }

    /**
     * 第三步:
     * 加载已安装的模块
     */
    private static Module loadInstalledModule(String packageName, boolean forceLoadNew, Map<String, Plugin> newLoadedPlugins) throws PluginInstallException {
        boolean loadNew = false;
        Module result = null;
        if (host != null && host.getConfig().getPackageName().equals(packageName)) {
            result = null;
        } else if (!forceLoadNew && loadedModules.containsKey(packageName)) {
            result = loadedModules.get(packageName);
        } else {
            // 创建module
            try {
                InstallInfo installInfo = InstallInfo.readInstallInfo(getInstallInfoFile(packageName));
                if (installInfo.state == InstallInfo.State.DISABLE) {
                    throw new PluginDisableException(packageName);
                }

                File moduleFile = getInstalledModuleFile(packageName);
                if (moduleFile == null || !moduleFile.exists()) {
                    // 如果未找到插件, 尝试从assets中在安装一次.
                    moduleFile = installFromAssets(packageName + ASSETS_MODULE_NAME_SUFFIX);
                }
                Config config = ConfigHelper.getModuleConfig(moduleFile, true);
                if (config != null) {
                    result = new Module(new ModuleContext(moduleFile, config), config);
                    result.init();
                    loadedModules.put(packageName, result);
                    newLoadedPlugins.put(packageName, result);
                    result.onLoaded();
                    loadNew = true;
                } else {
                    throw new IOException("read config error: " + packageName);
                }
            } catch (Throwable ex) {
                throw new PluginInstallException("load module error", ex, packageName);
            }
        }

        if (loadNew) {
            // 加载依赖
            loadDependence(result, newLoadedPlugins);
        }

        return result;
    }

    /**
     * @param plugin
     * @param newLoadedPlugins
     * @throws PluginInstallException
     */
    private static void loadDependence(Plugin plugin, Map<String, Plugin> newLoadedPlugins) throws PluginInstallException {
        Set<String> dependence = plugin.getConfig().getDependence();
        if (dependence != null) {
            PluginInstallException exception = new PluginInstallException("loading dependence error", null);
            for (String packageName : dependence) {
                try {
                    loadInstalledModule(packageName, false, newLoadedPlugins);
                } catch (Throwable ex) {
                    exception.addPackageName(packageName);
                    exception.addEx(ex);
                }
            }
            if (exception.packageNameListCount() > 0) {
                throw exception;
            }
        }
    }

    /**
     * 从assets中安装插件包(仅在加载不到插件时尝试调用此方法)
     *
     * @param fileName
     * @return
     * @throws IOException
     */
    private static File installFromAssets(String fileName) throws IOException {
        AssetManager assets = x.app().getAssets();
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = assets.open(ASSETS_MODULE_DIR_NAME + "/" + fileName);
            File tempFile = getTempModuleFile(fileName);
            out = new FileOutputStream(tempFile);
            IOUtil.copy(in, out);
            return installTempModuleFile(tempFile);
        } finally {
            IOUtil.closeQuietly(in);
            IOUtil.closeQuietly(out);
        }
    }

    /**
     * 获取安装信息文件
     *
     * @param packageName
     * @return
     */
    /* package */
    static File getInstallInfoFile(String packageName) {
        File installDir = x.app().getDir(MODULE_INSTALL_DIR_NAME, 0);
        File packageDir = new File(installDir, packageName);
        return new File(packageDir, CONFIG_INSTALL_INFO_FILE_NAME);
    }

    /**
     * 获取插件文件目录
     */
    private static File getModuleDir(Config config) {
        File installDir = x.app().getDir(MODULE_INSTALL_DIR_NAME, 0);
        File packageDir = new File(installDir, config.packageName);
        File currVerDir = new File(packageDir, String.valueOf(config.version));
        if (currVerDir.exists() || currVerDir.mkdirs()) {
            return currVerDir;
        }
        return null;
    }

    /**
     * 获取插件文件
     *
     * @param config
     * @return
     */
    private static File getModuleFile(Config config) throws IOException {
        File result = null;
        File moduleDir = getModuleDir(config);
        if (moduleDir != null && moduleDir.exists()) {
            result = new File(moduleDir, MODULE_APP_NAME);
        }
        return result;
    }

    /**
     * 获取已安装的插件文件目录
     */
    private static File getInstalledModuleDir(String packageName) throws IOException {
        File result = null;
        File installInfoFile = getInstallInfoFile(packageName);
        if (installInfoFile.exists()) {
            InstallInfo installInfo = InstallInfo.readInstallInfo(installInfoFile);
            if (installInfo != null) {
                File installDir = x.app().getDir(MODULE_INSTALL_DIR_NAME, 0);
                File packageDir = new File(installDir, packageName);
                result = new File(packageDir, installInfo.pluginDirName);
            }
        }
        return result;
    }

    /**
     * 获取已安装的插件文件
     *
     * @param packageName
     * @return
     */
    private static File getInstalledModuleFile(String packageName) throws IOException {
        File result = null;
        File moduleDir = getInstalledModuleDir(packageName);
        if (moduleDir.exists()) {
            result = new File(moduleDir, MODULE_APP_NAME);
        }
        return result;
    }

    /**
     * 临时的插件文件目录
     *
     * @return
     */
    private static File getTempModuleDir() {
        File tempDir = x.app().getDir(MODULE_INSTALL_TEMP_DIR_NAME, 0);
        if (tempDir.exists() || tempDir.mkdirs()) {
            return tempDir;
        } else {
            return null;
        }
    }

    /**
     * 临时的插件文件
     *
     * @param fileName
     * @return
     */
    private static File getTempModuleFile(String fileName) {
        File tempDir = getTempModuleDir();
        if (tempDir != null) {
            return new File(tempDir, fileName);
        }
        return null;
    }

    /**
     * 获取需要清理的旧文件
     */
    private static List<File> getOldPluginFiles() throws IOException {
        List<File> result = new ArrayList<File>(1);
        File installDir = x.app().getDir(MODULE_INSTALL_DIR_NAME, 0);
        File[] pkgDirs = installDir.listFiles();
        if (pkgDirs != null) {
            for (File pkgDir : pkgDirs) {
                if (pkgDir.isDirectory()) {
                    File installInfoFile = new File(pkgDir, CONFIG_INSTALL_INFO_FILE_NAME);
                    if (installInfoFile.exists()) {
                        InstallInfo installInfo = InstallInfo.readInstallInfo(installInfoFile);
                        File[] verFiles = pkgDir.listFiles();
                        String installInfoFileName = installInfoFile.getName();
                        if (verFiles != null) {
                            for (File verDir : verFiles) {
                                String verDirName = verDir.getName();
                                if (!verDirName.equals(installInfoFileName)) {
                                    if (verDirName.equals(installInfo.pluginDirName)) {
                                        if (!new File(verDir, MODULE_APP_NAME).exists()) {
                                            result.add(pkgDir); // 删除整个包目录
                                        }
                                    } else {
                                        result.add(verDir); // 删除该版本
                                    }
                                }
                            }
                        }
                    } else {
                        result.add(pkgDir); // 删除整个包目录
                    }
                }
            }
        }
        return result;
    }

    /**
     * 获取assets自带插件的版本文件
     *
     * @return
     */
    private static File getAssetsVersionFile() {
        File installDir = x.app().getDir(MODULE_INSTALL_DIR_NAME, 0);
        if (installDir.exists() || installDir.mkdirs()) {
            return new File(installDir, ASSETS_MODULE_VERSION_NAME);
        }
        return null;
    }

    /**
     * 设置assets中插件解压后的版本
     */
    private synchronized static void setAssetsModulesDecompressed() {
        FileOutputStream out = null;
        try {
            File versionFile = getAssetsVersionFile();
            out = new FileOutputStream(versionFile, false);
            IOUtil.writeStr(out, String.valueOf(getAppInstallTime()));
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        } finally {
            IOUtil.closeQuietly(out);
        }
    }

    /**
     * 判断assets中插件是否已解压
     */
    private synchronized static boolean isAssetsModulesDecompressed() {
        FileInputStream in = null;
        try {
            File versionFile = getAssetsVersionFile();
            if (versionFile != null && versionFile.exists()) {
                in = new FileInputStream(versionFile);
                String versionMark = IOUtil.readStr(in).trim();
                return versionMark.equals(String.valueOf(getAppInstallTime()));
            }
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        } finally {
            IOUtil.closeQuietly(in);
        }
        return false;
    }

    private static long getAppInstallTime() {
        return new File(x.app().getApplicationInfo().sourceDir).lastModified();
    }

    // ###################################### init task ######################################
    private static class InitTask extends AbsTask<Void> {

        private ProcessLock processLock;
        private final Map<String, Plugin> newLoadedPlugins = new HashMap<String, Plugin>();

        public InitTask() {
        }

        @Override
        public Priority getPriority() {
            return Priority.UI_TOP;
        }

        @Override
        public Executor getExecutor() {
            return EXECUTOR;
        }

        @Override
        protected Void doBackground() throws Throwable {
            // supplement host config
            ConfigHelper.supplementHostConfig(host.getConfig());

            host.onLoaded();

            // post host loaded event
            x.task().post(new Runnable() {
                @Override
                public void run() {
                    HashMap<String, Plugin> temp = new HashMap<String, Plugin>(1);
                    temp.put(host.getConfig().getPackageName(), host);
                    installEvent.onPluginsLoaded(host, temp);
                }
            });

            processLock = ProcessLock.tryLock(PROCESS_LOCK_NAME, true, PROCESS_LOCK_TIMEOUT);
            if (processLock == null || !processLock.isValid()) {
                throw new PluginInstallLockException();
            }

            final PluginInstallException exception = new PluginInstallException("init task error", null);
            synchronized (initLock) {
                // try delete modules form uninstall list
                try {
                    List<File> oldPluginFiles = getOldPluginFiles();
                    if (oldPluginFiles != null) {
                        for (File oldFile : oldPluginFiles) {
                            IOUtil.deleteFileOrDir(oldFile);
                        }
                    }
                } catch (Throwable ex) {
                    LogUtil.d("delete old files of modules", ex);
                }

                // init installedModules
                synchronized (installedModules) {
                    File installDir = x.app().getDir(MODULE_INSTALL_DIR_NAME, 0);
                    File[] pkgDirs = installDir != null ? installDir.listFiles() : null;
                    if (pkgDirs != null) {
                        for (File pkgDir : pkgDirs) {
                            if (pkgDir.isDirectory()) {
                                String pkgName = pkgDir.getName();
                                String[] children = pkgDir.list();
                                if (children != null && children.length > 0) {
                                    try {
                                        File moduleFile = getInstalledModuleFile(pkgName);
                                        if (moduleFile != null && moduleFile.exists()) {
                                            installedModules.add(pkgName);
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. decompress modules from assets
                try {
                    decompressAssetsModules();
                } catch (Throwable ex) {
                    exception.addEx(ex);
                }

                // 1.2 cp external modules to the temp folder
                try {
                    File sdcard = Environment.getExternalStorageDirectory();
                    File extModulesDir = new File(sdcard, EXTERNAL_PLUGIN_DIR_NAME);
                    if (extModulesDir.exists()) {
                        File[] files = extModulesDir.listFiles();
                        if (files != null) {
                            for (File externalFile : files) {
                                if (externalFile.isFile()) {
                                    InputStream in = null;
                                    FileOutputStream out = null;
                                    Config config = ConfigHelper.getModuleConfig(externalFile, false);
                                    try {
                                        in = new FileInputStream(externalFile);
                                        out = new FileOutputStream(getTempModuleFile(config.packageName));
                                        IOUtil.copy(in, out);
                                    } finally {
                                        IOUtil.closeQuietly(in);
                                        IOUtil.closeQuietly(out);
                                    }
                                    try {
                                        IOUtil.deleteFileOrDir(externalFile);
                                    } catch (Throwable ex) {
                                        LogUtil.d(ex.getMessage(), ex);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ex) {
                    LogUtil.d(ex.getMessage(), ex);
                }

                // 2. install all modules from the temp folder
                File tempDir = getTempModuleDir();
                if (tempDir != null) {
                    File[] tempPluginFiles = tempDir.listFiles();
                    if (tempPluginFiles != null) {
                        for (File tempPluginFile : tempPluginFiles) {
                            try {
                                installTempModuleFile(tempPluginFile);
                            } catch (Throwable ex) {
                                exception.addEx(ex);
                            }
                        }
                    }
                }

                // 3. load runtime module
                try {
                    String runtimePkg = PluginRuntime.getRuntimeListener().getRuntimePkg();
                    if (!TextUtils.isEmpty(runtimePkg)) {
                        runtimeModule = loadInstalledModule(runtimePkg, false, this.newLoadedPlugins);
                        if (runtimeModule != null) {
                            HostContextProxy.onRuntimeModuleLoaded(runtimeModule);
                        }
                    }
                } catch (Throwable ex) {
                    exception.addEx(ex);
                }

                // 4. load host dependence
                try {
                    loadDependence(host, this.newLoadedPlugins);
                } catch (Throwable ex) {
                    exception.addEx(ex);
                }

                // init installedModules
                synchronized (installedModules) {
                    File installDir = x.app().getDir(MODULE_INSTALL_DIR_NAME, 0);
                    File[] pkgDirs = installDir != null ? installDir.listFiles() : null;
                    if (pkgDirs != null) {
                        for (File pkgDir : pkgDirs) {
                            if (pkgDir.isDirectory()) {
                                String pkgName = pkgDir.getName();
                                String[] children = pkgDir.list();
                                if (children != null && children.length > 0) {
                                    try {
                                        File moduleFile = getInstalledModuleFile(pkgName);
                                        if (moduleFile != null && moduleFile.exists()) {
                                            installedModules.add(pkgName);
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }
                        }
                    }
                }

                initFinished = true;
                initLock.notifyAll();
            }

            IOUtil.closeQuietly(processLock);

            if (exception.exListCount() > 0) {
                throw exception;
            }
            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            installEvent.onHostInitialised(false);

            if (!this.newLoadedPlugins.isEmpty()) {
                installEvent.onPluginsLoaded(null, this.newLoadedPlugins);
            }
        }

        @Override
        protected void onError(Throwable ex, boolean isCallbackError) {
            // 初始化失败, 删除版本标记, 重启时再次初始化.
            File versionFile = getAssetsVersionFile();
            IOUtil.deleteFileOrDir(versionFile);

            installEvent.onPluginsLoadError(ex, isCallbackError);
            installEvent.onHostInitialised(true);

            if (!this.newLoadedPlugins.isEmpty()) {
                installEvent.onPluginsLoaded(null, this.newLoadedPlugins);
            }
        }

        @Override
        protected void onFinished() {
            initFinished = true;
            IOUtil.closeQuietly(processLock);
        }
    }

    // ###################################### install task ######################################
    public static class InstallTask extends AbsTask<Module> {

        private final File externalFile;
        private Callback.CommonCallback<Module> callback;
        private ProcessLock processLock;
        private final Map<String, Plugin> newLoadedPlugins = new HashMap<String, Plugin>();

        public InstallTask(File externalFile, Callback.CommonCallback<Module> callback) {
            this.externalFile = externalFile;
            this.callback = callback;
        }

        @Override
        public Executor getExecutor() {
            return EXECUTOR;
        }

        @Override
        protected Module doBackground() throws Throwable {
            processLock = ProcessLock.tryLock(PROCESS_LOCK_NAME, true, PROCESS_LOCK_TIMEOUT);
            if (processLock == null || !processLock.isValid()) {
                throw new PluginInstallLockException();
            }

            Module result = null;

            File tempFile = null;
            Config config = null;
            boolean needRestart = false;

            // 1. 解析config.
            // 如果存在已安装的相同版本提示用户, 停止安装.
            config = ConfigHelper.getModuleConfig(this.externalFile, true);
            if (loadedModules.containsKey(config.packageName)) {
                Plugin plugin = getLoadedPlugin(config.packageName);
                if (config.version == plugin.getConfig().version) {
                    // 存在已安装的相同版本提示用户, 需要重启安装.
                    throw new PluginAlreadyLoadedException(config.packageName, config.version);
                } else {
                    needRestart = true;
                }
            }

            // 2. 拷贝到安装临时文件.
            FileOutputStream out = null;
            InputStream in = null;
            try {
                in = new FileInputStream(this.externalFile);
                tempFile = getTempModuleFile(config.packageName);
                out = new FileOutputStream(tempFile);
                IOUtil.copy(in, out);
            } finally {
                IOUtil.closeQuietly(in);
                IOUtil.closeQuietly(out);
            }

            // 3. 安装
            installTempModuleFile(tempFile);

            synchronized (installedModules) {
                installedModules.add(config.packageName);
            }

            // 4. 加载
            if (!needRestart) {
                result = loadInstalledModule(config.packageName, true, this.newLoadedPlugins);
                IOUtil.closeQuietly(processLock);
            } else {
                IOUtil.closeQuietly(processLock);
                throw new PluginNeedRestartException(config.packageName);
            }

            return result;
        }

        @Override
        protected void onSuccess(Module result) {
            if (result != null || !this.newLoadedPlugins.isEmpty()) {
                installEvent.onPluginsLoaded(result, this.newLoadedPlugins);
            }
            if (this.callback != null) {
                this.callback.onSuccess(result);
            }
        }

        @Override
        protected void onError(Throwable ex, boolean isCallbackError) {
            if (this.callback != null) {
                this.callback.onError(ex, isCallbackError);
            } else {
                installEvent.onPluginsLoadError(ex, isCallbackError);
            }
            if (!this.newLoadedPlugins.isEmpty()) {
                installEvent.onPluginsLoaded(null, this.newLoadedPlugins);
            }
        }

        @Override
        protected void onFinished() {
            IOUtil.closeQuietly(processLock);
            if (this.callback != null) {
                this.callback.onFinished();
            }
        }
    }

    // ###################################### load task ######################################
    public static class LoadTask extends AbsTask<Module> {

        private final String packageName;
        private Callback.CommonCallback<Module> callback;
        private ProcessLock processLock;
        private final Map<String, Plugin> newLoadedPlugins = new HashMap<String, Plugin>();

        public LoadTask(String packageName, Callback.CommonCallback<Module> callback) {
            this.packageName = packageName;
            this.callback = callback;
        }

        @Override
        public Executor getExecutor() {
            return EXECUTOR;
        }

        @Override
        protected Module doBackground() throws Throwable {
            processLock = ProcessLock.tryLock(PROCESS_LOCK_NAME, true, PROCESS_LOCK_TIMEOUT);
            if (processLock == null || !processLock.isValid()) {
                throw new PluginInstallLockException();
            }
            Module result = loadInstalledModule(this.packageName, false, this.newLoadedPlugins);
            IOUtil.closeQuietly(processLock);
            return result;
        }

        @Override
        protected void onSuccess(Module result) {
            installEvent.onPluginsLoaded(result, this.newLoadedPlugins);
            if (this.callback != null) {
                this.callback.onSuccess(result);
            }
        }

        @Override
        protected void onError(Throwable ex, boolean isCallbackError) {
            if (this.callback != null) {
                this.callback.onError(ex, isCallbackError);
            } else {
                installEvent.onPluginsLoadError(ex, isCallbackError);
            }
            if (!this.newLoadedPlugins.isEmpty()) {
                installEvent.onPluginsLoaded(null, this.newLoadedPlugins);
            }
        }

        @Override
        protected void onFinished() {
            IOUtil.closeQuietly(processLock);
            if (this.callback != null) {
                this.callback.onFinished();
            }
        }
    }

    // ################################# uninstall task ############################
    public static class UninstallTask extends AbsTask<Boolean> {

        private final String packageName;
        private Callback.CommonCallback<Boolean> callback;
        private ProcessLock processLock;

        public UninstallTask(String packageName, Callback.CommonCallback<Boolean> callback) {
            this.packageName = packageName;
            this.callback = callback;
        }

        @Override
        public Executor getExecutor() {
            return EXECUTOR;
        }

        @Override
        protected Boolean doBackground() throws Throwable {
            processLock = ProcessLock.tryLock(PROCESS_LOCK_NAME, true, PROCESS_LOCK_TIMEOUT);
            if (processLock == null || !processLock.isValid()) {
                throw new PluginInstallLockException();
            }

            boolean needRestart = loadedModules.containsKey(packageName);
            File path = getInstalledModuleDir(packageName);
            if (!IOUtil.deleteFileOrDir(path)) {
                needRestart = true;
            }

            synchronized (installedModules) {
                installedModules.remove(packageName);
            }

            Module module = loadedModules.remove(packageName);
            if (module != null) {
                module.onDestroy();
            }

            IOUtil.closeQuietly(processLock);

            return needRestart;
        }

        @Override
        protected void onSuccess(Boolean result) {
            if (this.callback != null) {
                this.callback.onSuccess(result);
            }
        }

        @Override
        protected void onError(Throwable ex, boolean isCallbackError) {
            if (this.callback != null) {
                this.callback.onError(ex, isCallbackError);
            }
        }

        @Override
        protected void onFinished() {
            IOUtil.closeQuietly(processLock);
            if (this.callback != null) {
                this.callback.onFinished();
            }
        }
    }

    // ################################# plugin state task ############################
    public static class ChangeStateTask extends AbsTask<Void> {

        private final String packageName;
        private final InstallInfo.State state;
        private Callback.CommonCallback<Void> callback;
        private ProcessLock processLock;

        public ChangeStateTask(String packageName, InstallInfo.State state, Callback.CommonCallback<Void> callback) {
            this.packageName = packageName;
            this.state = state;
            this.callback = callback;
        }

        @Override
        public Executor getExecutor() {
            return EXECUTOR;
        }

        @Override
        protected Void doBackground() throws Throwable {
            processLock = ProcessLock.tryLock(PROCESS_LOCK_NAME, true, PROCESS_LOCK_TIMEOUT);
            if (processLock == null || !processLock.isValid()) {
                throw new PluginInstallLockException();
            }

            InstallInfo.writeInstallInfo(
                    new InstallInfo(state, getInstalledModuleDir(packageName).getName()),
                    getInstallInfoFile(packageName));

            if (this.state == InstallInfo.State.DISABLE) {
                Module module = loadedModules.remove(packageName);
                if (module != null) {
                    module.onDestroy();
                }
            }

            IOUtil.closeQuietly(processLock);

            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            if (this.callback != null) {
                this.callback.onSuccess(result);
            }
        }

        @Override
        protected void onError(Throwable ex, boolean isCallbackError) {
            if (this.callback != null) {
                this.callback.onError(ex, isCallbackError);
            }
        }

        @Override
        protected void onFinished() {
            IOUtil.closeQuietly(processLock);
            if (this.callback != null) {
                this.callback.onFinished();
            }
        }
    }

    // ###################################### load config task ######################################
    public static class LoadConfigTask extends AbsTask<Config> {

        private final String packageNameOrPath;
        private Callback.CommonCallback<Config> callback;

        public LoadConfigTask(String packageNameOrPath, Callback.CommonCallback<Config> callback) {
            this.packageNameOrPath = packageNameOrPath;
            this.callback = callback;
        }

        @Override
        public Executor getExecutor() {
            return EXECUTOR;
        }

        @Override
        protected Config doBackground() throws Throwable {
            File pluginFile = new File(packageNameOrPath);
            if (pluginFile.exists()) {
                return ConfigHelper.getModuleConfig(pluginFile, false);
            } else {
                Plugin plugin = getLoadedPlugin(packageNameOrPath);
                if (plugin != null) {
                    return plugin.getConfig();
                } else {
                    return ConfigHelper.getModuleConfig(getInstalledModuleFile(packageNameOrPath), false);
                }
            }
        }

        @Override
        protected void onSuccess(Config result) {
            if (this.callback != null) {
                this.callback.onSuccess(result);
            }
        }

        @Override
        protected void onError(Throwable ex, boolean isCallbackError) {
            if (this.callback != null) {
                this.callback.onError(ex, isCallbackError);
            }
        }

        @Override
        protected void onFinished() {
            if (this.callback != null) {
                this.callback.onFinished();
            }
        }
    }
}
