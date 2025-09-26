package ru.neverlands.abclient.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class UserConfig {
    public String UserNick;
    public String UserPassword;
    public String UserPasswordFlash;
    public boolean UserAutoLogon;
    public boolean UseProxy;
    public String ProxyAddress;
    public String ProxyUserName;
    public String ProxyPassword;
    public String ConfigHash;
    public long LastLogin;
    public String TorgTabl;
    public boolean ChatKeepMoving;
    public long ServDiff;
    public int FishUm;
    public boolean LightForum;
    public String TorgMessageTooExp;
    public String TorgMessageLess90;
    public String TorgDeny;
    public boolean TorgSliv;
    public String TorgMessageThanks;
    public String TorgMessageNoMoney;
    public boolean DoProxy;
    public boolean DoPromptExit;
    public boolean DoHttpLog;
    public boolean DoTexLog;
    public boolean ShowPerformance;
    public boolean AutoFish;
    public boolean AutoHerb;
    public boolean AutoMine;
    public boolean AutoTree;
    public boolean AutoDig;
    public boolean AutoTorg;
    public boolean TorgActive;

    public UserConfig() {
        this.UserNick = "";
        this.UserPassword = "";
        this.UserPasswordFlash = "";
        this.UserAutoLogon = false;
        this.UseProxy = false;
        this.ProxyAddress = "";
        this.ProxyUserName = "";
        this.ProxyPassword = "";
        this.ConfigHash = "";
        this.LastLogin = 0;
        this.TorgTabl = "";
        this.ChatKeepMoving = false;
        this.ServDiff = 0;
        this.FishUm = 0;
        this.LightForum = false;
        this.TorgMessageTooExp = "";
        this.TorgMessageLess90 = "";
        this.TorgDeny = "";
        this.TorgSliv = false;
        this.TorgMessageThanks = "";
        this.TorgMessageNoMoney = "";
        this.DoProxy = false;
        this.DoPromptExit = false;
        this.DoHttpLog = false;
        this.DoTexLog = false;
        this.ShowPerformance = false;
        this.AutoFish = false;
        this.AutoHerb = false;
        this.AutoMine = false;
        this.AutoTree = false;
        this.AutoDig = false;
        this.AutoTorg = false;
        this.TorgActive = false;
    }

    public String getTorgTabl() {
        return TorgTabl;
    }

    public int getFishUm() {
        return FishUm;
    }

    public void setFishUm(int fishUm) {
        FishUm = fishUm;
    }

    public boolean isLightForum() {
        return LightForum;
    }

    public String getUserNick() {
        return UserNick;
    }

    public String getUserPassword() {
        return UserPassword;
    }

    public String getTorgMessageTooExp() {
        return TorgMessageTooExp;
    }

    public String getTorgMessageLess90() {
        return TorgMessageLess90;
    }

    public String getTorgDeny() {
        return TorgDeny;
    }

    public boolean isTorgSliv() {
        return TorgSliv;
    }

    public String getTorgMessageThanks() {
        return TorgMessageThanks;
    }

    public String getTorgMessageNoMoney() {
        return TorgMessageNoMoney;
    }

    public static void saveProfiles(Context context, List<UserConfig> profiles) {
        SharedPreferences prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(profiles);
        editor.putString("profiles_json", json);
        editor.apply();
    }

    public static List<UserConfig> loadProfiles(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString("profiles_json", null);
        Type type = new TypeToken<ArrayList<UserConfig>>() {}.getType();
        List<UserConfig> profiles = gson.fromJson(json, type);
        if (profiles == null) {
            profiles = new ArrayList<>();
        }
        return profiles;
    }

    public static UserConfig load(Context context) {
        List<UserConfig> profiles = loadProfiles(context);
        if (profiles.isEmpty()) {
            return new UserConfig();
        }

        UserConfig lastUsed = profiles.get(0);
        for (UserConfig profile : profiles) {
            if (profile.LastLogin > lastUsed.LastLogin) {
                lastUsed = profile;
            }
        }
        return lastUsed;
    }

    public void save(Context context) {
        List<UserConfig> profiles = loadProfiles(context);
        int index = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).UserNick.equals(this.UserNick)) {
                index = i;
                break;
            }
        }

        this.LastLogin = System.currentTimeMillis();

        if (index != -1) {
            profiles.set(index, this);
        } else {
            profiles.add(this);
        }

        saveProfiles(context, profiles);
    }

    @Override
    public String toString() {
        return UserNick;
    }
}
