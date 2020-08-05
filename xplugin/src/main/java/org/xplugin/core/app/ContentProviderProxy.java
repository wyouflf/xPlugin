package org.xplugin.core.app;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.install.Installer;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.io.FileNotFoundException;
import java.util.HashMap;

public class ContentProviderProxy extends ContentProvider {

    public static final String AUTHORITY_SUFFIX = ".xPlugin.Provider";

    /**
     * key: authority
     */
    private static final HashMap<String, ContentProvider> CONTENT_PROVIDER_MAP = new HashMap<String, ContentProvider>(5);

    public ContentProviderProxy() {
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.getType(getRealUri(uri));
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.insert(realUri, values);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.delete(realUri, selection, selectionArgs);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.query(realUri, projection, selection, selectionArgs, sortOrder);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.query(realUri, projection, selection, selectionArgs, sortOrder, cancellationSignal);
    }

    @Override
    @SuppressLint("NewApi")
    public Cursor query(Uri uri, String[] projection, Bundle queryArgs, CancellationSignal cancellationSignal) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.query(realUri, projection, queryArgs, cancellationSignal);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.update(realUri, values, selection, selectionArgs);
    }

    @Override
    public Uri canonicalize(Uri uri) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.canonicalize(getRealUri(uri));
    }

    @Override
    public Uri uncanonicalize(Uri uri) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.uncanonicalize(getRealUri(uri));
    }

    @Override
    @SuppressLint("NewApi")
    public boolean refresh(Uri uri, Bundle args, CancellationSignal cancellationSignal) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.refresh(realUri, args, cancellationSignal);
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.bulkInsert(realUri, values);
    }

    @Override
    @SuppressLint("NewApi")
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.openFile(realUri, mode, null);
    }

    @Override
    @SuppressLint("NewApi")
    public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.openFile(realUri, mode, signal);
    }

    @Override
    @SuppressLint("NewApi")
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.openAssetFile(realUri, mode, null);
    }

    @Override
    @SuppressLint("NewApi")
    public AssetFileDescriptor openAssetFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.openAssetFile(realUri, mode, signal);
    }

    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.getStreamTypes(realUri, mimeTypeFilter);
    }

    @Override
    @SuppressLint("NewApi")
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts) throws FileNotFoundException {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.openTypedAssetFile(realUri, mimeTypeFilter, opts, null);
    }

    @Override
    @SuppressLint("NewApi")
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts, CancellationSignal signal) throws FileNotFoundException {
        Uri realUri = getRealUri(uri);
        ContentProvider provider = getRealContentProvider(realUri.getAuthority());
        return provider.openTypedAssetFile(realUri, mimeTypeFilter, opts, signal);
    }

    private static Uri getRealUri(Uri raw) {
        String authority = raw.getAuthority();
        if (authority != null && authority.endsWith(AUTHORITY_SUFFIX)) {
            String uriString = raw.toString();
            uriString = uriString.replaceAll(authority + '/', "");
            return Uri.parse(uriString);
        } else {
            return raw;
        }
    }

    public static ContentProvider getRealContentProvider(String authority) {
        ContentProvider contentProvider = CONTENT_PROVIDER_MAP.get(authority);
        if (contentProvider != null) return contentProvider;

        try {
            ProviderInfo info = x.app().getPackageManager()
                    .resolveContentProvider(authority, PackageManager.GET_META_DATA);
            if (info != null) {
                Plugin plugin = Installer.getLoadedPlugin(info.packageName);
                if (plugin != null) {
                    contentProvider = (ContentProvider) plugin.loadClass(info.name).newInstance();
                } else {
                    contentProvider = (ContentProvider) Installer.loadClass(info.name).newInstance();
                }
                contentProvider.attachInfo(Plugin.getPlugin(contentProvider).getContext(), info);
                contentProvider.onCreate();
                CONTENT_PROVIDER_MAP.put(authority, contentProvider);
            }
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }

        return contentProvider;
    }
}
