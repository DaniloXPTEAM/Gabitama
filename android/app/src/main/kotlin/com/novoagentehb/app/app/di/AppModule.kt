package com.novoagentehb.app.app.di

import android.content.Context
import androidx.room.Room
import com.novoagentehb.app.data.local.MIGRATION_1_2
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.novoagentehb.app.data.local.AppDatabase
import com.novoagentehb.app.data.local.BrowserHistoryDao
import com.novoagentehb.app.data.local.ChatMessageDao
import com.novoagentehb.app.data.local.FeedEventDao
import com.novoagentehb.app.data.local.GoalDao
import com.novoagentehb.app.data.local.MemoryDao
import com.novoagentehb.app.data.local.ReminderDao
import com.novoagentehb.app.data.local.SkillRecordDao
import com.novoagentehb.app.data.local.TaskReceiptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides fun provideMemoryDao(db: AppDatabase): MemoryDao = db.memoryDao()
    @Provides fun provideGoalDao(db: AppDatabase): GoalDao = db.goalDao()
    @Provides fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
    @Provides fun provideFeedEventDao(db: AppDatabase): FeedEventDao = db.feedEventDao()
    @Provides fun provideTaskReceiptDao(db: AppDatabase): TaskReceiptDao = db.taskReceiptDao()
    @Provides fun provideSkillRecordDao(db: AppDatabase): SkillRecordDao = db.skillRecordDao()
    @Provides fun provideBrowserHistoryDao(db: AppDatabase): BrowserHistoryDao = db.browserHistoryDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideMcpChatToolRunner(
        client: com.novoagentehb.app.agent.skills.McpJsonRpcClient,
        registry: com.novoagentehb.app.agent.skills.SkillAndMcpRegistry,
    ): com.novoagentehb.app.agent.skills.McpChatToolRunner =
        com.novoagentehb.app.agent.skills.McpChatToolRunner(client) { id -> registry.getById(id) }
}
