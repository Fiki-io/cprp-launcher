package com.cprp.launcher;

import android.content.Context;
import android.content.SharedPreferences;

public class GameConfig {

    public static final String PREFS_NAME = "cprp_launcher_prefs";
    public static final String KEY_NICKNAME = "pref_nickname";
    public static final String KEY_SERVER_IP = "pref_server_ip";
    public static final String KEY_SERVER_PORT = "pref_server_port";
    public static final String KEY_GAME_DIR = "pref_game_dir";

    public static final String DEFAULT_NICKNAME = "Player_CPRP";
    public static final String DEFAULT_SERVER_IP = "samp.cprp.me";
    public static final int DEFAULT_SERVER_PORT = 7777;
    public static final String DEFAULT_GAME_DIR = "/sdcard/GTA_SA";
    public static final String EXE_NAME = "gta_sa.exe";

    private String nickname;
    private String serverIp;
    private int serverPort;
    private String gameDir;

    public GameConfig(String nickname, String serverIp, int serverPort, String gameDir) {
        this.nickname = nickname;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.gameDir = gameDir;
    }

    public static GameConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String nickname = prefs.getString(KEY_NICKNAME, DEFAULT_NICKNAME);
        String serverIp = prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP);
        int serverPort = prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT);
        String gameDir = prefs.getString(KEY_GAME_DIR, DEFAULT_GAME_DIR);
        return new GameConfig(nickname, serverIp, serverPort, gameDir);
    }

    public void save(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_NICKNAME, nickname)
                .putString(KEY_SERVER_IP, serverIp)
                .putInt(KEY_SERVER_PORT, serverPort)
                .putString(KEY_GAME_DIR, gameDir)
                .apply();
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getGameDir() {
        return gameDir;
    }

    public void setGameDir(String gameDir) {
        this.gameDir = gameDir;
    }
}
