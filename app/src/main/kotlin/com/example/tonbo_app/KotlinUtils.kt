package com.example.tonbo_app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * Kotlin工具類
 * 展示Kotlin與Java的互操作性
 */
object KotlinUtils {
    private const val TAG = "KotlinUtils"
    
    /**
     * 使用Kotlin協程進行異步操作
     */
    fun performAsyncOperation(context: Context, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 在IO線程執行耗時操作
                val result = withContext(Dispatchers.IO) {
                    delay(1000) // 模擬網絡請求
                    "Kotlin異步操作完成 - ${context.packageName}"
                }
                
                // 回到主線程更新UI
                callback(result)
                Log.d(TAG, "Kotlin協程操作成功: $result")
                
            } catch (e: Exception) {
                Log.e(TAG, "Kotlin協程操作失敗: ${e.message}")
                callback("操作失敗")
            }
        }
    }
    
    /**
     * 數據類示例 - Kotlin的簡潔語法
     */
    data class UserInfo(
        val name: String,
        val language: String,
        val isAccessibilityEnabled: Boolean = true
    )
    
    /**
     * 擴展函數示例 - 為String添加新功能
     */
    fun String.isValidCommand(): Boolean {
        return this.isNotBlank() && this.length > 2
    }
    
    /**
     * 高階函數示例
     */
    fun processVoiceCommands(
        commands: List<String>,
        processor: (String) -> Boolean
    ): List<String> {
        return commands.filter { command ->
            command.isValidCommand() && processor(command)
        }
    }
    
    /**
     * 空安全示例
     */
    fun safeGetLanguage(context: Context?): String {
        return context?.let { ctx ->
            // 安全地訪問context
            "Kotlin安全訪問: ${ctx.packageName}"
        } ?: "Context為空"
    }
}
