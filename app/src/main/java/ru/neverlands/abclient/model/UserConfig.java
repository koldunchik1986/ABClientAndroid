package ru.neverlands.abclient.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import java.util.UUID;

public class UserConfig {
    public String id;
    public String UserNick;
    public String UserPassword;
    public String UserPasswordFlash;
    public boolean isEncrypted;
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
    public int TorgMinLevel;
    public String TorgEx;
    public boolean ShowTrayBaloons;
    public boolean FishChatReport;
    public boolean DoGuamod;
    public boolean DoInvPack;
    public boolean DoInvSort;
    public boolean ChatKeepGame;

    public UserConfig() {
        this.id = UUID.randomUUID().toString();
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
        this.TorgMinLevel = 16;
        this.TorgEx = "силы;ловкости;удачи;зелье";
        this.ShowTrayBaloons = true;
        this.FishChatReport = false;
        this.DoGuamod = false;
        this.DoInvPack = true;
        this.DoInvSort = true;
        this.ChatKeepGame = true;
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

    public String getUserPasswordFlash() {
        return UserPasswordFlash;
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
        // Deprecated: No longer saving all profiles at once.
        for (UserConfig profile : profiles) {
            profile.save(context);
        }
    }

    public static List<UserConfig> loadAllProfiles(Context context) {
        List<UserConfig> profiles = new ArrayList<>();
        File profilesDir = ru.neverlands.abclient.utils.DataManager.getProfilesDir();
        if (profilesDir == null || !profilesDir.exists()) {
            return profiles;
        }

        File[] profileFiles = profilesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".profile"));

        if (profileFiles == null) {
            return profiles;
        }

        Gson gson = new Gson();
        for (File file : profileFiles) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                UserConfig profile = gson.fromJson(json, UserConfig.class);
                if (profile != null) {
                    profiles.add(profile);
                }
            } catch (IOException e) {
                android.util.Log.e("UserConfig", "Error loading profile: " + file.getName(), e);
            }
        }
        return profiles;
    }

    public static UserConfig load(Context context, String userNick) {
        File profileFile = new File(ru.neverlands.abclient.utils.DataManager.getProfilesDir(), userNick + ".profile");
        if (!profileFile.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(profileFile)) {
            byte[] data = new byte[(int) profileFile.length()];
            fis.read(data);
            String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            return new Gson().fromJson(json, UserConfig.class);
        } catch (IOException e) {
            android.util.Log.e("UserConfig", "Error loading profile: " + userNick, e);
            return null;
        }
    }

    public void save(Context context) {
        if (this.UserNick == null || this.UserNick.isEmpty()) {
            android.util.Log.e("UserConfig", "Cannot save profile with empty UserNick.");
            return;
        }
        this.LastLogin = System.currentTimeMillis();
        Gson gson = new Gson();
        String json = gson.toJson(this);
        File profileFile = new File(ru.neverlands.abclient.utils.DataManager.getProfilesDir(), this.UserNick + ".profile");
        ru.neverlands.abclient.utils.DataManager.writeStringToFile(profileFile, json);
    }


    @Override
    public String toString() {
        return UserNick;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserConfig that = (UserConfig) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }
}
