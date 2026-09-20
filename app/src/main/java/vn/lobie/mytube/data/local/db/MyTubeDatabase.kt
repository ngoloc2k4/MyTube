package vn.lobie.mytube.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import vn.lobie.mytube.data.local.db.dao.DownloadDao
import vn.lobie.mytube.data.local.db.dao.HiddenVideoDao
import vn.lobie.mytube.data.local.db.dao.LikedVideoDao
import vn.lobie.mytube.data.local.db.dao.PlaylistDao
import vn.lobie.mytube.data.local.db.dao.PlaylistVideoDao
import vn.lobie.mytube.data.local.db.dao.SubscriptionDao
import vn.lobie.mytube.data.local.db.dao.WatchHistoryDao
import vn.lobie.mytube.data.local.db.entity.DownloadEntity
import vn.lobie.mytube.data.local.db.entity.HiddenVideoEntity
import vn.lobie.mytube.data.local.db.entity.LikedVideoEntity
import vn.lobie.mytube.data.local.db.entity.PlaylistEntity
import vn.lobie.mytube.data.local.db.entity.PlaylistVideoEntity
import vn.lobie.mytube.data.local.db.entity.SubscriptionEntity
import vn.lobie.mytube.data.local.db.entity.WatchHistoryEntity

@Database(
    entities = [
        WatchHistoryEntity::class,
        LikedVideoEntity::class,
        SubscriptionEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
        DownloadEntity::class,
        HiddenVideoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MyTubeDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun likedVideoDao(): LikedVideoDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistVideoDao(): PlaylistVideoDao
    abstract fun downloadDao(): DownloadDao
    abstract fun hiddenVideoDao(): HiddenVideoDao

    companion object {
        @Volatile
        private var INSTANCE: MyTubeDatabase? = null

        fun getInstance(context: Context): MyTubeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MyTubeDatabase::class.java,
                    "mytube_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
