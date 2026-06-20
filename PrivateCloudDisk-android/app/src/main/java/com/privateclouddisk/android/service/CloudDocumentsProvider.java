package com.privateclouddisk.android.service;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.os.CancellationSignal;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import androidx.annotation.Nullable;

import com.privateclouddisk.android.data.model.NodeItem;
import com.privateclouddisk.android.data.remote.ApiClient;
import com.privateclouddisk.android.data.repository.FileRepository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * 云端文档提供者
 *
 * 对应 macOS 的 FileProviderExtension
 * 让私有云文件在系统文件管理器中以虚拟磁盘的形式呈现
 * 用户可在其他 App 中直接浏览、打开云端文件
 */
public class CloudDocumentsProvider extends DocumentsProvider {

    private static final String DEFAULT_ROOT = "cloud_root";
    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_ICON
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private FileRepository fileRepository;
    private File cacheDir;

    @Override
    public boolean onCreate() {
        cacheDir = new File(getContext().getCacheDir(), "cloud_files");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(
                projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, DEFAULT_ROOT);
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                        | DocumentsContract.Root.FLAG_LOCAL_ONLY);
        row.add(DocumentsContract.Root.COLUMN_TITLE, "私有云盘");
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getDocId(""));
        row.add(DocumentsContract.Root.COLUMN_ICON, com.privateclouddisk.android.R.mipmap.ic_launcher);
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, @Nullable String[] projection)
            throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);

        if (DEFAULT_ROOT.equals(documentId) || "".equals(getParentId(documentId))) {
            // 根目录
            addRow(cursor, "", "私有云盘", DocumentsContract.Document.MIME_TYPE_DIR,
                    DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE, 0, 0);
        } else {
            // 文件/文件夹
            String fileId = getParentId(documentId);
            try {
                NodeItem item = getFileRepository().getFileDetail(fileId)
                        .blockingGet();
                if (item != null) {
                    String mimeType = item.isDirectory()
                            ? DocumentsContract.Document.MIME_TYPE_DIR
                            : getMimeType(item.getEffectiveName());
                    int flags = item.isDirectory()
                            ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                            : 0;
                    addRow(cursor, fileId, item.getEffectiveName(), mimeType, flags,
                            item.getEffectiveSize(),
                            parseTimestamp(item.getUpdatedTime()));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to query document");
                throw new FileNotFoundException("Document not found: " + documentId);
            }
        }
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId,
                                       @Nullable String[] projection,
                                       @Nullable String sortOrder)
            throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);

        String parentId = DEFAULT_ROOT.equals(parentDocumentId) ? "" : getParentId(parentDocumentId);

        try {
            List<NodeItem> items = getFileRepository().getFileList(parentId, 1, 100)
                    .blockingFirst();

            if (items != null) {
                for (NodeItem item : items) {
                    String mimeType = item.isDirectory()
                            ? DocumentsContract.Document.MIME_TYPE_DIR
                            : getMimeType(item.getEffectiveName());
                    int flags = item.isDirectory()
                            ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                            : 0 | DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
                    addRow(cursor, item.getEffectiveId(), item.getEffectiveName(),
                            mimeType, flags, item.getEffectiveSize(),
                            parseTimestamp(item.getUpdatedTime()));
                }
            }
        } catch (Exception e) {
            Timber.e(e, "Failed to query child documents");
        }

        return cursor;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
                                              @Nullable CancellationSignal signal)
            throws FileNotFoundException {

        // 只支持只读模式
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Write mode not supported yet");
        }

        try {
            String fileId = getParentId(documentId);
            NodeItem item = getFileRepository().getFileDetail(fileId).blockingGet();
            if (item == null) {
                throw new FileNotFoundException("File not found");
            }

            // 获取操作凭证
            String token = getFileRepository().getOperationToken(fileId, "download").blockingGet();

            // 下载到缓存目录
            File cacheFile = new File(cacheDir, fileId + "_" + item.getEffectiveName());

            if (!cacheFile.exists()) {
                downloadToCache(fileId, token, cacheFile);
            }

            return ParcelFileDescriptor.open(cacheFile,
                    ParcelFileDescriptor.MODE_READ_ONLY);

        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            Timber.e(e, "Failed to open document");
            throw new FileNotFoundException("Failed to open: " + e.getMessage());
        }
    }

    private void downloadToCache(String fileId, String token, File cacheFile) throws Exception {
        retrofit2.Response<ResponseBody> response =
                getApiClient().getFileApi().downloadFile(fileId, token).execute();

        if (response.isSuccessful() && response.body() != null) {
            try (InputStream is = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(cacheFile)) {
                FileUtils.copy(is, fos);
            }
        }
    }

    // ── 辅助方法 ──

    private void addRow(MatrixCursor cursor, String id, String name,
                         String mimeType, int flags, long size, long lastModified) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, getDocId(id));
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name);
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType);
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags);
        row.add(DocumentsContract.Document.COLUMN_SIZE, size);
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified);
    }

    private String getDocId(String fileId) {
        return "cloud_" + (fileId != null ? fileId : "");
    }

    private String getParentId(String docId) {
        if (docId == null || !docId.startsWith("cloud_")) return "";
        return docId.substring(6);
    }

    private long parseTimestamp(String timeStr) {
        if (timeStr == null) return System.currentTimeMillis();
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            return sdf.parse(timeStr).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private String getMimeType(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            case "svg": return "image/svg+xml";
            case "mp4": return "video/mp4";
            case "avi": return "video/x-msvideo";
            case "mov": return "video/quicktime";
            case "mp3": return "audio/mpeg";
            case "wav": return "audio/wav";
            case "aac": return "audio/aac";
            case "flac": return "audio/flac";
            case "pdf": return "application/pdf";
            case "doc": case "docx": return "application/msword";
            case "xls": case "xlsx": return "application/vnd.ms-excel";
            case "ppt": case "pptx": return "application/vnd.ms-powerpoint";
            case "txt": return "text/plain";
            case "csv": return "text/csv";
            case "html": return "text/html";
            case "zip": return "application/zip";
            case "apk": return "application/vnd.android.package-archive";
            default: return "application/octet-stream";
        }
    }

    private FileRepository getFileRepository() {
        // TODO: Proper DI integration required for production use
        if (fileRepository == null) {
            throw new IllegalStateException("CloudDocumentsProvider not properly initialized");
        }
        return fileRepository;
    }

    private ApiClient getApiClient() {
        // TODO: Proper DI integration required for production use
        throw new IllegalStateException("CloudDocumentsProvider not properly initialized");
    }
}