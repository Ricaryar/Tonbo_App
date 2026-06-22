package com.example.tonbo_app;

import java.util.HashMap;
import java.util.Map;

/**
 * 語音命令映射構建器
 * 使用Builder模式優化命令初始化
 */
public class VoiceCommandBuilder {
    
    private final Map<String, String> commandMap = new HashMap<>();
    
    /**
     * 添加命令映射
     */
    public VoiceCommandBuilder addCommand(String voiceCommand, String action) {
        commandMap.put(voiceCommand, action);
        return this;
    }
    
    /**
     * 批量添加命令
     */
    public VoiceCommandBuilder addCommands(String action, String... voiceCommands) {
        for (String voiceCommand : voiceCommands) {
            commandMap.put(voiceCommand, action);
        }
        return this;
    }
    
    /**
     * 構建命令映射
     */
    public Map<String, String> build() {
        return new HashMap<>(commandMap);
    }
    
    /**
     * 創建廣東話命令映射
     */
    public static Map<String, String> buildCantoneseCommands() {
        return new VoiceCommandBuilder()
            // 環境識別相關
            .addCommands("open_environment", "打開環境識別", "打开环境识别", "环境识别", "睇下周圍", "环境", "識別環境", "识别环境", "打開環境", "打开环境", "環境檢測", "环境检测")
            // 環境識別控制
            .addCommands("start_detection", "開始檢測", "開始識別", "開始掃描", "開始環境識別")
            .addCommands("stop_detection", "停止檢測", "停止識別", "停止掃描", "停止環境識別")
            .addCommands("describe_environment", "描述環境", "描述周圍", "講下周圍", "周圍有咩")
            // 文件閱讀
            .addCommands("open_document", "打開閱讀助手", "打开阅读助手", "閱讀助手", "阅读助手", "讀文件", "读文件", "掃描文件", "扫描文件", "文件助手", "閱讀", "阅读", "掃描", "扫描")
            // 尋找物品
            .addCommands("open_find", "尋找物品", "寻找物品", "搵嘢", "找東西", "找东西", "尋找", "寻找", "搵物品", "找物品")
            // 即時協助
            .addCommands("open_assistance", "即時協助", "幫手", "協助", "即時幫助", "需要幫助")
            // 緊急求助
            .addCommands("emergency", "緊急求助", "救命", "幫我", "緊急", "求助", "緊急情況")
            // 導航
            .addCommands("go_home", "返回主頁", "返回主页", "主頁", "主页", "回到主頁", "回到主页", "去主頁", "去主页", "主畫面", "主画面")
            .addCommands("go_back", "返回", "返回上一頁", "上一頁", "後退")
            // 語言切換
            .addCommands("switch_language", "切換語言", "轉語言", "換語言", "改變語言")
            // 設定
            .addCommands("open_settings", "打開設定", "打开设定", "打開設置", "打开设置", "設定", "设定", "設置", "设置", "系統設定", "系统设定", "打開設置")
            // 時間查詢
            .addCommands("tell_time", "現在幾點", "幾點", "時間", "現在時間", "幾點鐘")
            // 控制命令
            .addCommands("stop_listening", "停止", "收聲", "停止監聽", "停止語音", "停止識別")
            .addCommands("repeat", "重複", "再說一次", "重複剛才", "再說一遍")
            .addCommands("volume_up", "增大音量", "音量增大", "大聲啲", "提高音量")
            .addCommands("volume_down", "減小音量", "音量減小", "細聲啲", "降低音量")
            // 聊天記錄命令
            .addCommands("view_chat_history", "查看聊天記錄", "聊天記錄", "對話記錄", "查看記錄", "歷史記錄")
            .addCommands("previous_message", "上一句", "上一條", "上一條消息", "前一句")
            .addCommands("next_message", "下一句", "下一條", "下一條消息", "後一句")
            .addCommands("repeat_last_message", "重複上一句", "再說上一句", "重複剛才的話")
            .addCommands("clear_chat_history", "清除聊天記錄", "清空記錄", "清除記錄")
            // 幫助和指導命令
            .addCommands("help", "幫助", "幫我", "使用說明", "操作指南", "點用", "點樣用", "教學")
            .addCommands("what_can_i_say", "我可以講咩", "有咩指令", "可用指令", "命令列表", "指令列表", "有咩命令")
            .addCommands("list_commands", "列出指令", "顯示指令", "所有指令", "指令清單")
            // 狀態查詢
            .addCommands("what_page", "我在邊頁", "現在邊頁", "當前頁面", "呢頁係咩", "咩頁面")
            .addCommands("where_am_i", "我在邊度", "現在位置", "當前位置", "邊度")
            .addCommands("current_function", "當前功能", "現在功能", "呢個功能", "功能係咩")
            // 屏幕閱讀增強
            .addCommands("read_screen", "讀屏幕", "讀畫面", "讀內容", "讀出屏幕", "屏幕內容")
            .addCommands("read_focused_item", "讀焦點", "讀選中", "讀當前", "讀呢個", "焦點內容")
            .addCommands("read_all_items", "讀全部", "讀所有", "讀晒", "全部內容", "所有項目")
            // 導航增強
            .addCommands("next_item", "下一個", "下項", "下一個項目", "下一個選項", "下一個功能")
            .addCommands("previous_item", "上一個", "上項", "上一個項目", "上一個選項", "上一個功能")
            .addCommands("select_item", "選擇", "選呢個", "確認選擇", "選中", "確定")
            // 確認和取消
            .addCommands("confirm", "確認", "確定", "係", "好", "OK", "同意")
            .addCommands("cancel", "取消", "唔要", "唔好", "唔使", "不要", "否")
            .addCommands("yes", "係", "好", "對", "正確", "同意", "要")
            .addCommands("no", "唔係", "唔好", "唔要", "不對", "不要", "否")
            // 手勢管理
            .addCommands("open_gesture", "打開手勢", "手勢管理", "手勢設定", "手勢", "管理手勢")
            // 檢測控制增強
            .addCommands("pause_detection", "暫停檢測", "暫停識別", "暫停掃描", "暫停")
            .addCommands("resume_detection", "繼續檢測", "繼續識別", "繼續掃描", "恢復檢測", "繼續")
            .addCommands("toggle_detection", "切換檢測", "開關檢測", "檢測開關")
            // 快捷操作
            .addCommands("quick_help", "快速幫助", "快捷幫助", "快速說明", "快速指南")
            .build();
    }
    
    /**
     * 創建英文命令映射
     */
    public static Map<String, String> buildEnglishCommands() {
        return new VoiceCommandBuilder()
            // 環境識別相關
            .addCommands("open_environment", "open environment", "environment recognition", "look around", "environment", "detect environment", "scan environment")
            // 環境識別控制
            .addCommands("start_detection", "start detection", "start scanning", "begin detection", "start recognizing")
            .addCommands("stop_detection", "stop detection", "stop scanning", "end detection", "stop recognizing")
            .addCommands("describe_environment", "describe environment", "describe surroundings", "what's around", "tell me what's around")
            // 文件閱讀
            .addCommands("open_document", "open document", "document assistant", "read document", "scan document", "document", "read", "scan")
            // 尋找物品
            .addCommands("open_find", "find items", "find object", "find things", "search items", "locate items")
            // 即時協助
            .addCommands("open_assistance", "live assistance", "help", "assistance", "need help", "get help")
            // 緊急求助
            .addCommands("emergency", "emergency", "help me", "emergency help", "urgent help", "call emergency")
            // 導航
            .addCommands("go_home", "go home", "home", "return home", "back to home", "main screen")
            .addCommands("go_back", "go back", "back", "previous page", "return")
            // 語言切換
            .addCommands("switch_language", "switch language", "change language", "change to", "set language")
            // 設定
            .addCommands("open_settings", "open settings", "settings", "system settings", "preferences")
            // 時間查詢
            .addCommands("tell_time", "what time is it", "tell me the time", "time", "current time", "what's the time")
            // 控制命令
            .addCommands("stop_listening", "stop", "stop listening", "stop voice", "stop recognition", "cancel")
            .addCommands("repeat", "repeat", "say again", "repeat that", "say it again")
            .addCommands("volume_up", "volume up", "increase volume", "louder", "turn up volume")
            .addCommands("volume_down", "volume down", "decrease volume", "quieter", "turn down volume")
            // 聊天記錄命令
            .addCommands("view_chat_history", "view chat history", "chat history", "conversation history", "show history", "history")
            .addCommands("previous_message", "previous message", "last message", "previous", "go back")
            .addCommands("next_message", "next message", "next", "go forward")
            .addCommands("repeat_last_message", "repeat last message", "say last message again", "repeat previous")
            .addCommands("clear_chat_history", "clear chat history", "clear history", "delete history")
            // Help and guidance commands
            .addCommands("help", "help", "help me", "user guide", "how to use", "instructions", "tutorial")
            .addCommands("what_can_i_say", "what can I say", "available commands", "what commands", "command list", "list commands", "show commands")
            .addCommands("list_commands", "list commands", "show commands", "all commands", "command list")
            // Status queries
            .addCommands("what_page", "what page", "current page", "where am I", "which page", "page name")
            .addCommands("where_am_i", "where am I", "current location", "my location", "where")
            .addCommands("current_function", "current function", "what function", "which function", "function name")
            // Screen reading enhancement
            .addCommands("read_screen", "read screen", "read content", "read page", "read all", "screen content")
            .addCommands("read_focused_item", "read focused", "read selected", "read current", "read this", "focused item")
            .addCommands("read_all_items", "read all", "read everything", "read all items", "all content", "all items")
            // Navigation enhancement
            .addCommands("next_item", "next", "next item", "next option", "next function", "move forward")
            .addCommands("previous_item", "previous", "previous item", "previous option", "previous function", "move back")
            .addCommands("select_item", "select", "choose", "select this", "confirm selection", "activate")
            // Confirmation and cancellation
            .addCommands("confirm", "confirm", "yes", "ok", "okay", "agree", "accept")
            .addCommands("cancel", "cancel", "no", "don't", "abort", "decline", "reject")
            .addCommands("yes", "yes", "yeah", "yep", "correct", "right", "agree")
            .addCommands("no", "no", "nope", "wrong", "disagree", "not", "decline")
            // Gesture management
            .addCommands("open_gesture", "open gesture", "gesture management", "gesture settings", "gestures", "manage gestures")
            // Detection control enhancement
            .addCommands("pause_detection", "pause detection", "pause scanning", "pause", "stop temporarily")
            .addCommands("resume_detection", "resume detection", "continue detection", "resume scanning", "continue", "resume")
            .addCommands("toggle_detection", "toggle detection", "switch detection", "detection toggle")
            // Quick operations
            .addCommands("quick_help", "quick help", "fast help", "quick guide", "quick instructions")
            .build();
    }
    
    /**
     * 創建普通話命令映射
     */
    public static Map<String, String> buildMandarinCommands() {
        return new VoiceCommandBuilder()
            // 環境識別相關
            .addCommands("open_environment", "打開環境識別", "環境識別", "看看周圍", "環境", "識別環境", "打開環境", "環境檢測")
            // 環境識別控制
            .addCommands("start_detection", "開始檢測", "開始識別", "開始掃描", "開始環境識別")
            .addCommands("stop_detection", "停止檢測", "停止識別", "停止掃描", "停止環境識別")
            .addCommands("describe_environment", "描述環境", "描述周圍", "說說周圍", "周圍有什麼")
            // 文件閱讀
            .addCommands("open_document", "打開閱讀助手", "閱讀助手", "讀文件", "掃描文件", "文件助手", "閱讀", "掃描")
            // 尋找物品
            .addCommands("open_find", "尋找物品", "找東西", "尋找", "找物品", "搜索物品")
            // 即時協助
            .addCommands("open_assistance", "即時協助", "幫助", "協助", "即時幫助", "需要幫助")
            // 緊急求助
            .addCommands("emergency", "緊急求助", "救命", "幫我", "緊急", "求助", "緊急情況")
            // 導航
            .addCommands("go_home", "返回主頁", "主頁", "回到主頁", "去主頁", "主畫面")
            .addCommands("go_back", "返回", "返回上一頁", "上一頁", "後退")
            // 語言切換
            .addCommands("switch_language", "切換語言", "轉換語言", "換語言", "改變語言")
            // 設定
            .addCommands("open_settings", "打開設置", "設置", "系統設置", "打開設定")
            // 時間查詢
            .addCommands("tell_time", "現在幾點", "幾點了", "時間", "現在時間", "幾點鐘")
            // 控制命令
            .addCommands("stop_listening", "停止", "停", "停止監聽", "停止語音", "停止識別")
            .addCommands("repeat", "重複", "再說一次", "重複剛才", "再說一遍")
            .addCommands("volume_up", "增大音量", "音量增大", "大聲點", "提高音量")
            .addCommands("volume_down", "減小音量", "音量減小", "小聲點", "降低音量")
            // 聊天記錄命令
            .addCommands("view_chat_history", "查看聊天記錄", "聊天記錄", "對話記錄", "查看記錄", "歷史記錄")
            .addCommands("previous_message", "上一句", "上一條", "上一條消息", "前一句")
            .addCommands("next_message", "下一句", "下一條", "下一條消息", "後一句")
            .addCommands("repeat_last_message", "重複上一句", "再說上一句", "重複剛才的話")
            .addCommands("clear_chat_history", "清除聊天記錄", "清空記錄", "清除記錄")
            // 幫助和指導命令
            .addCommands("help", "幫助", "幫我", "使用說明", "操作指南", "怎麼用", "如何使用")
            .addCommands("what_can_i_say", "我可以說什麼", "有什麼指令", "可用指令", "命令列表", "指令列表", "有什麼命令")
            .addCommands("list_commands", "列出指令", "顯示指令", "所有指令", "指令清單")
            // 狀態查詢
            .addCommands("what_page", "我在哪頁", "現在哪頁", "當前頁面", "這頁是什麼", "什麼頁面")
            .addCommands("where_am_i", "我在哪裡", "現在位置", "當前位置", "哪裡")
            .addCommands("current_function", "當前功能", "現在功能", "這個功能", "功能是什麼")
            // 屏幕閱讀增強
            .addCommands("read_screen", "讀屏幕", "讀畫面", "讀內容", "讀出屏幕", "屏幕內容")
            .addCommands("read_focused_item", "讀焦點", "讀選中", "讀當前", "讀這個", "焦點內容")
            .addCommands("read_all_items", "讀全部", "讀所有", "讀所有內容", "全部內容", "所有項目")
            // 導航增強
            .addCommands("next_item", "下一個", "下一項", "下一個項目", "下一個選項", "下一個功能")
            .addCommands("previous_item", "上一個", "上一項", "上一個項目", "上一個選項", "上一個功能")
            .addCommands("select_item", "選擇", "選這個", "確認選擇", "選中", "確定")
            // 確認和取消
            .addCommands("confirm", "確認", "確定", "是", "好", "OK", "同意")
            .addCommands("cancel", "取消", "不要", "不好", "不用", "不要", "否")
            .addCommands("yes", "是", "好", "對", "正確", "同意", "要")
            .addCommands("no", "不是", "不好", "不要", "不對", "不要", "否")
            // 手勢管理
            .addCommands("open_gesture", "打開手勢", "手勢管理", "手勢設置", "手勢", "管理手勢")
            // 檢測控制增強
            .addCommands("pause_detection", "暫停檢測", "暫停識別", "暫停掃描", "暫停")
            .addCommands("resume_detection", "繼續檢測", "繼續識別", "繼續掃描", "恢復檢測", "繼續")
            .addCommands("toggle_detection", "切換檢測", "開關檢測", "檢測開關")
            // 快捷操作
            .addCommands("quick_help", "快速幫助", "快捷幫助", "快速說明", "快速指南")
            .build();
    }
}
