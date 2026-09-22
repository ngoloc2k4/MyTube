package vn.lobie.mytube.data.importer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ExternalDataManager {
    private const val TAG = "ExternalDataManager"
    private const val BACKUP_FILENAME = "mytube_backup.json"
    private const val DB_BACKUP_FILENAME = "mytube_database.db"

    const val CANONICAL_PATH = "/storage/emulated/0/MyTube/data"

    /**
     * Đường dẫn thư mục lưu trữ bên ngoài chuẩn Android: /storage/emulated/0/MyTube/data/
     */
    fun getDataDirectory(): File {
        val canonical = File(CANONICAL_PATH)
        val parent = canonical.parentFile
        if (parent?.exists() == true || canonical.exists()) {
            return canonical
        }
        val root = Environment.getExternalStorageDirectory()
        return File(root, "MyTube/data")
    }

    /**
     * Kiểm tra ứng dụng đã có quyền đọc ghi bộ nhớ ngoài chưa
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Điều hướng người dùng mở màn hình cấp quyền bộ nhớ ngoài
     */
    fun requestStoragePermission(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else if (context is Activity) {
                context.requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    1001
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request storage permission", e)
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Ghi toàn bộ dữ liệu hiện tại vào /storage/emulated/0/MyTube/data/
     * Bao gồm:
     * 1. File JSON cấu trúc: mytube_backup.json (độc lập OS, phục hồi an toàn)
     * 2. Snapshot file SQLite database: mytube_database.db
     */
    suspend fun saveToExternalStorage(
        context: Context,
        database: MyTubeDatabase
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = getDataDirectory()
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (!created && !dir.exists()) {
                    return@withContext Result.failure(
                        IllegalStateException("Không thể tạo thư mục tại ${dir.absolutePath}. Hãy kiểm tra quyền truy cập bộ nhớ.")
                    )
                }
            }

            // 1. Xuất file JSON qua BackupRestoreManager
            val backupManager = BackupRestoreManager(context, database)
            val (jsonContent, _) = backupManager.generateBackupJsonString()

            val jsonFile = File(dir, BACKUP_FILENAME)
            val tempJsonFile = File(dir, "$BACKUP_FILENAME.tmp")

            FileOutputStream(tempJsonFile).use { fos ->
                fos.write(jsonContent.toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            if (tempJsonFile.exists()) {
                if (jsonFile.exists()) {
                    jsonFile.delete()
                }
                tempJsonFile.renameTo(jsonFile)
            }

            // 2. Snapshot bản sao SQLite database
            try {
                // Checkpoint SQLite WAL buffer trước khi sao chép
                try {
                    val cursor = database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
                    cursor.close()
                } catch (e: Exception) {
                    Log.w(TAG, "wal_checkpoint warning: ${e.message}")
                }

                val originalDbFile = context.getDatabasePath("mytube_database")
                if (originalDbFile.exists()) {
                    val targetDbFile = File(dir, DB_BACKUP_FILENAME)
                    FileInputStream(originalDbFile).use { input ->
                        FileOutputStream(targetDbFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot snapshot raw db file (JSON backup still succeeded): ${e.message}")
            }

            Log.i(TAG, "Saved external backup successfully to ${jsonFile.absolutePath}")
            Result.success(jsonFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to external storage", e)
            Result.failure(e)
        }
    }

    /**
     * Khôi phục dữ liệu từ file /storage/emulated/0/MyTube/data/mytube_backup.json
     */
    suspend fun restoreFromExternalStorage(
        context: Context,
        database: MyTubeDatabase
    ): Result<RestoreStats> = withContext(Dispatchers.IO) {
        try {
            val dir = getDataDirectory()
            val jsonFile = File(dir, BACKUP_FILENAME)
            if (!jsonFile.exists()) {
                return@withContext Result.failure(
                    java.io.FileNotFoundException("Không tìm thấy tệp sao lưu tại ${jsonFile.absolutePath}")
                )
            }

            val content = jsonFile.readText(Charsets.UTF_8)
            val backupManager = BackupRestoreManager(context, database)
            backupManager.restoreBackupFromString(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from external storage", e)
            Result.failure(e)
        }
    }

    /**
     * Tự động kiểm tra và khôi phục khi cài lại app:
     * Nếu cơ sở dữ liệu hiện tại đang rỗng (mới cài lại app)
     * và trong /sdcard/MyTube/data/ có dữ liệu cũ -> Tự động khôi phục ngay.
     */
    suspend fun checkAndAutoRestoreIfEmpty(
        context: Context,
        database: MyTubeDatabase
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val subsCount = database.subscriptionDao().getAllList().size
            val historyCount = database.watchHistoryDao().getAllList().size

            // Chỉ tự động restore nếu DB hoàn toàn mới (0 subs, 0 history)
            if (subsCount == 0 && historyCount == 0) {
                val dir = getDataDirectory()
                val jsonFile = File(dir, BACKUP_FILENAME)
                if (jsonFile.exists() && jsonFile.length() > 0) {
                    Log.i(TAG, "Phát hiện tệp sao lưu cũ tại ${jsonFile.absolutePath}, bắt đầu tự động khôi phục...")
                    val result = restoreFromExternalStorage(context, database)
                    if (result.isSuccess) {
                        Log.i(TAG, "Tự động khôi phục dữ liệu thành công!")
                        return@withContext true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "checkAndAutoRestoreIfEmpty error: ${e.message}")
            false
        }
    }
}
