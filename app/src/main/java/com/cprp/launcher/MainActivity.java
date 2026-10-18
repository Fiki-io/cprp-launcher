package com.cprp.launcher;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvSubtitle;
    private TextView tvLogOutput;
    private ScrollView scrollLog;

    private EditText etNickname;
    private EditText etServerIp;
    private EditText etServerPort;
    private EditText etGameDir;

    private Button btnInstallAssets;
    private Button btnLaunchGame;
    private Button btnTestVersion;
    private Button btnTestFlags;
    private Button btnTestHelp;
    private Button btnClearLog;

    private GameConfig gameConfig;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gameConfig = GameConfig.load(this);

        initViews();
        bindConfigToViews();
        setupConfigWatchers();
        setupListeners();
        checkCoreStatus();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvLogOutput = findViewById(R.id.tvLogOutput);
        scrollLog = findViewById(R.id.scrollLog);

        etNickname = findViewById(R.id.etNickname);
        etServerIp = findViewById(R.id.etServerIp);
        etServerPort = findViewById(R.id.etServerPort);
        etGameDir = findViewById(R.id.etGameDir);

        btnInstallAssets = findViewById(R.id.btnInstallAssets);
        btnLaunchGame = findViewById(R.id.btnLaunchGame);
        btnTestVersion = findViewById(R.id.btnTestVersion);
        btnTestFlags = findViewById(R.id.btnTestFlags);
        btnTestHelp = findViewById(R.id.btnTestHelp);
        btnClearLog = findViewById(R.id.btnClearLog);
    }

    private void bindConfigToViews() {
        etNickname.setText(gameConfig.getNickname());
        etServerIp.setText(gameConfig.getServerIp());
        etServerPort.setText(String.valueOf(gameConfig.getServerPort()));
        etGameDir.setText(gameConfig.getGameDir());

        String archInfo = getString(R.string.subtitle_arch) + ": " +
                (Process.is64Bit() ? "Box64 (arm64-v8a)" : "Box86 (armeabi-v7a)");
        tvSubtitle.setText(archInfo);
    }

    private void setupConfigWatchers() {
        TextWatcher autoSaveWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                saveCurrentConfig();
            }
        };

        etNickname.addTextChangedListener(autoSaveWatcher);
        etServerIp.addTextChangedListener(autoSaveWatcher);
        etServerPort.addTextChangedListener(autoSaveWatcher);
        etGameDir.addTextChangedListener(autoSaveWatcher);
    }

    private void saveCurrentConfig() {
        String nickname = etNickname.getText().toString().trim();
        String serverIp = etServerIp.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();
        String gameDir = etGameDir.getText().toString().trim();

        int port = GameConfig.DEFAULT_SERVER_PORT;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ignored) {}

        gameConfig.setNickname(nickname);
        gameConfig.setServerIp(serverIp);
        gameConfig.setServerPort(port);
        gameConfig.setGameDir(gameDir);
        gameConfig.save(this);
    }

    private void setupListeners() {
        btnInstallAssets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performAssetInstallation();
            }
        });

        btnLaunchGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performGameLaunch();
            }
        });

        btnTestVersion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runCoreCommand(new String[]{"box86", "-v"});
            }
        });

        btnTestFlags.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runCoreCommand(new String[]{"box86", "-f"});
            }
        });

        btnTestHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runCoreCommand(new String[]{"box86", "-h"});
            }
        });

        btnClearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvLogOutput.setText(getString(R.string.log_cleared));
            }
        });
    }

    private void performAssetInstallation() {
        saveCurrentConfig();
        final String gameDirPath = gameConfig.getGameDir();
        if (gameDirPath.isEmpty()) {
            appendLog(getString(R.string.error_dir_empty));
            return;
        }

        final File targetDir = new File(gameDirPath);
        if (!targetDir.exists()) {
            boolean created = targetDir.mkdirs();
            if (!created) {
                appendLog(getString(R.string.error_dir_not_found) + " [" + gameDirPath + "]");
                return;
            }
        }

        setButtonsEnabled(false);
        tvStatus.setText(getString(R.string.status_installing_assets));
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning));
        appendLog(getString(R.string.status_installing_assets) + " -> " + targetDir.getAbsolutePath());

        executor.execute(new Runnable() {
            @Override
            public void run() {
                GameRuntimeManager.installRuntimeAssets(MainActivity.this, targetDir, new GameRuntimeManager.InstallCallback() {
                    @Override
                    public void onProgress(final String message) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                appendLog(message);
                            }
                        });
                    }

                    @Override
                    public void onFileCompleted(final String filename, final int current, final int total) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                appendLog("Tersalin: " + filename + " [" + current + "/" + total + "]");
                            }
                        });
                    }

                    @Override
                    public void onSuccess(final String message) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                tvStatus.setText(getString(R.string.status_assets_installed));
                                tvStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_success));
                                appendLog(getString(R.string.status_assets_installed));
                                setButtonsEnabled(true);
                            }
                        });
                    }

                    @Override
                    public void onError(final String errorMessage) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                tvStatus.setText(getString(R.string.status_failed));
                                tvStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_error));
                                appendLog("Error: " + errorMessage);
                                setButtonsEnabled(true);
                            }
                        });
                    }
                });
            }
        });
    }

    private void performGameLaunch() {
        saveCurrentConfig();

        String nickname = gameConfig.getNickname();
        if (nickname.isEmpty()) {
            appendLog(getString(R.string.error_nickname_empty));
            return;
        }

        int port = gameConfig.getServerPort();
        if (port < 1 || port > 65535) {
            appendLog(getString(R.string.error_invalid_port));
            return;
        }

        String gameDir = gameConfig.getGameDir();
        GameRuntimeManager.VerificationStatus status = GameRuntimeManager.verifyGameDirectory(gameDir);

        if (status != GameRuntimeManager.VerificationStatus.SUCCESS) {
            switch (status) {
                case EMPTY_PATH:
                    appendLog(getString(R.string.error_dir_empty));
                    break;
                case NOT_FOUND:
                    appendLog(getString(R.string.error_dir_not_found) + " [" + gameDir + "]");
                    break;
                case NOT_A_DIRECTORY:
                    appendLog(getString(R.string.error_dir_not_directory));
                    break;
                case PERMISSION_DENIED:
                    appendLog(getString(R.string.error_dir_no_permission));
                    break;
                case MISSING_EXE:
                    appendLog(getString(R.string.error_missing_exe) + " (" + GameConfig.EXE_NAME + ")");
                    break;
            }
            return;
        }

        appendLog(getString(R.string.status_game_valid));
        String[] launchArgs = GameRuntimeManager.buildLaunchArguments(gameConfig);
        runCoreCommand(launchArgs);
    }

    private void checkCoreStatus() {
        if (NativeBridge.isLoaded()) {
            tvStatus.setText(getString(R.string.status_ready));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_success));
            appendLog(getString(R.string.status_ready) + " [" + NativeBridge.getLoadedCore() + "]");
        } else {
            tvStatus.setText(getString(R.string.status_failed));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error));
            appendLog("Error loading core: " + NativeBridge.getLoadError());
            setButtonsEnabled(false);
        }
    }

    private void runCoreCommand(final String[] args) {
        if (!NativeBridge.isLoaded()) {
            appendLog(getString(R.string.status_failed));
            return;
        }

        if (Process.is64Bit() && args.length > 0 && "box86".equals(args[0])) {
            args[0] = "box64";
        }

        setButtonsEnabled(false);
        tvStatus.setText(getString(R.string.status_running));
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning));

        StringBuilder commandStr = new StringBuilder();
        for (String arg : args) {
            commandStr.append(arg).append(" ");
        }
        appendLog(getString(R.string.log_executing_prefix) + " " + commandStr.toString().trim());

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final int exitCode = NativeBridge.startCore(args);
                    final String consoleLog = NativeBridge.pollLogOutput();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText(getString(R.string.status_finished));
                            tvStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_success));
                            appendLog(getString(R.string.log_exit_code) + " " + exitCode);
                            if (consoleLog != null && !consoleLog.trim().isEmpty()) {
                                appendLog(consoleLog.trim());
                            } else {
                                captureRecentLogcat();
                            }
                            setButtonsEnabled(true);
                        }
                    });
                } catch (final Throwable t) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText(getString(R.string.status_failed));
                            tvStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_error));
                            appendLog("Exception in startCore: " + t.getMessage());
                            setButtonsEnabled(true);
                        }
                    });
                }
            }
        });
    }

    private void captureRecentLogcat() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    java.lang.Process process = Runtime.getRuntime().exec("logcat -d -v time -s CPRP_CORE:V CPRP_CORE64:V BOX86:V BOX64:V");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    final StringBuilder logcatOutput = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logcatOutput.append(line).append("\n");
                    }
                    reader.close();

                    if (logcatOutput.length() > 0) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                appendLog("--- Logcat Stream ---\n" + logcatOutput.toString().trim());
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void appendLog(String message) {
        String timestamp = timeFormat.format(new Date());
        String currentText = tvLogOutput.getText().toString();
        String entry = "[" + timestamp + "] " + message + "\n";
        tvLogOutput.setText(currentText + "\n" + entry);
        scrollLog.post(new Runnable() {
            @Override
            public void run() {
                scrollLog.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        btnInstallAssets.setEnabled(enabled);
        btnLaunchGame.setEnabled(enabled);
        btnTestVersion.setEnabled(enabled);
        btnTestFlags.setEnabled(enabled);
        btnTestHelp.setEnabled(enabled);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
