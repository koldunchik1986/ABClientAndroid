package ru.neverlands.abclient.model;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public class UserConfig {
    // Основные настройки
    public String UserNick = "";
    public String UserKey = "";
    public String UserPassword = "";
    public boolean DoPromptExit = true;
    public boolean DoHttpLog = false;
    public boolean DoTexLog = false;
    public boolean ShowPerformance = false;
    public long ServDiff = 0;
    
    // Настройки прокси
    public boolean DoProxy = false;
    public String ProxyAddress = "";
    public String ProxyUserName = "";
    public String ProxyPassword = "";
    
    // Настройки автоматизации
    public boolean AutoFish = false;
    public boolean AutoHerb = false;
    public boolean AutoMine = false;
    public boolean AutoTree = false;
    public boolean AutoDig = false;
    public boolean AutoTorg = false;
    public boolean TorgActive = false;
    public boolean ChatKeepMoving = false;
    public boolean DoGuamod = false;
    public String UserPasswordFlash = "";

    public boolean LightForum = false;
    public int FishUm = 0;
    public String TorgTabl = "";
    public String TorgMessageTooExp = "";
    public String TorgMessageLess90 = "";
    public String TorgDeny = "";
    public boolean TorgSliv = false;
    public String TorgMessageThanks = "";
    public String TorgMessageNoMoney = "";
    
    // Настройки таймеров
    public List<AppConfigTimer> AppConfigTimers = new ArrayList<>();
    
    public static UserConfig load(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        UserConfig config = new UserConfig();
        
        config.UserNick = prefs.getString("user_nick", "");
        config.UserKey = prefs.getString("user_key", "");
        config.UserPassword = prefs.getString("user_password", "");
        config.DoPromptExit = prefs.getBoolean("do_prompt_exit", true);
        config.DoHttpLog = prefs.getBoolean("do_http_log", false);
        config.DoTexLog = prefs.getBoolean("do_tex_log", false);
        config.ShowPerformance = prefs.getBoolean("show_performance", false);
        
        config.DoProxy = prefs.getBoolean("do_proxy", false);
        config.ProxyAddress = prefs.getString("proxy_address", "");
        config.ProxyUserName = prefs.getString("proxy_username", "");
        config.ProxyPassword = prefs.getString("proxy_password", "");
        
        config.AutoFish = prefs.getBoolean("auto_fish", false);
        config.AutoHerb = prefs.getBoolean("auto_herb", false);
        config.AutoMine = prefs.getBoolean("auto_mine", false);
        config.AutoTree = prefs.getBoolean("auto_tree", false);
        config.AutoDig = prefs.getBoolean("auto_dig", false);
        config.AutoTorg = prefs.getBoolean("auto_torg", false);
        config.TorgActive = prefs.getBoolean("torg_active", false);

        config.LightForum = prefs.getBoolean("LightForum", false);
        config.FishUm = prefs.getInt("FishUm", 0);
        config.TorgTabl = prefs.getString("TorgTabl", "");
        config.TorgMessageTooExp = prefs.getString("torg_msg_too_exp", "");
        config.TorgMessageLess90 = prefs.getString("torg_msg_less_90", "");
        config.TorgDeny = prefs.getString("torg_deny", "");
        config.TorgSliv = prefs.getBoolean("torg_sliv", false);
        config.TorgMessageThanks = prefs.getString("torg_msg_thanks", "");
        config.TorgMessageNoMoney = prefs.getString("torg_msg_no_money", "");

        int timerCount = prefs.getInt("timer_count", 0);
        for (int i = 0; i < timerCount; i++) {
            AppConfigTimer timer = new AppConfigTimer();
            timer.Name = prefs.getString("timer_" + i + "_name", "");
            timer.Interval = prefs.getInt("timer_" + i + "_interval", 0);
            timer.Enabled = prefs.getBoolean("timer_" + i + "_enabled", false);
            config.AppConfigTimers.add(timer);
        }
        
        return config;
    }
    
    public void save(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString("user_nick", UserNick);
        editor.putString("user_key", UserKey);
        editor.putString("user_password", UserPassword);
        editor.putBoolean("do_prompt_exit", DoPromptExit);
        editor.putBoolean("do_http_log", DoHttpLog);
        editor.putBoolean("do_tex_log", DoTexLog);
        editor.putBoolean("show_performance", ShowPerformance);
        
        editor.putBoolean("do_proxy", DoProxy);
        editor.putString("proxy_address", ProxyAddress);
        editor.putString("proxy_username", ProxyUserName);
        editor.putString("proxy_password", ProxyPassword);
        
        editor.putBoolean("auto_fish", AutoFish);
        editor.putBoolean("auto_herb", AutoHerb);
        editor.putBoolean("auto_mine", AutoMine);
        editor.putBoolean("auto_tree", AutoTree);
        editor.putBoolean("auto_dig", AutoDig);
        editor.putBoolean("auto_torg", AutoTorg);
        editor.putBoolean("torg_active", TorgActive);

        editor.putBoolean("LightForum", LightForum);
        editor.putInt("FishUm", FishUm);
        editor.putString("TorgTabl", TorgTabl);
        editor.putString("torg_msg_too_exp", TorgMessageTooExp);
        editor.putString("torg_msg_less_90", TorgMessageLess90);
        editor.putString("torg_deny", TorgDeny);
        editor.putBoolean("torg_sliv", TorgSliv);
        editor.putString("torg_msg_thanks", TorgMessageThanks);
        editor.putString("torg_msg_no_money", TorgMessageNoMoney);
        
        editor.putInt("timer_count", AppConfigTimers.size());
        for (int i = 0; i < AppConfigTimers.size(); i++) {
            AppConfigTimer timer = AppConfigTimers.get(i);
            editor.putString("timer_" + i + "_name", timer.Name);
            editor.putInt("timer_" + i + "_interval", timer.Interval);
            editor.putBoolean("timer_" + i + "_enabled", timer.Enabled);
        }
        
        editor.apply();
    }

    public String getUserNick() { return UserNick; }
    public String getUserPassword() { return UserPassword; }
    public String getTorgTabl() { return TorgTabl; }
    public int getFishUm() { return FishUm; }
    public void setFishUm(int um) { this.FishUm = um; }
    public boolean isLightForum() { return LightForum; }
    public String getTorgMessageTooExp() { return TorgMessageTooExp; }
    public String getTorgMessageLess90() { return TorgMessageLess90; }
    public String getTorgDeny() { return TorgDeny; }
    public boolean isTorgSliv() { return TorgSliv; }
    public String getTorgMessageThanks() { return TorgMessageThanks; }
    public String getTorgMessageNoMoney() { return TorgMessageNoMoney; }
    public boolean isAutoLogon() { return false; }
    public long getLastLogon() { return 0; }
    public void setLastLogon(long lastLogon) { }
    public boolean isChatKeepMoving() { return ChatKeepMoving; }
    public boolean isTorgActive() { return TorgActive; }
    
    public static class AppConfigTimer {
        public String Name = "";
        public int Interval = 0;
        public boolean Enabled = false;
    }
}