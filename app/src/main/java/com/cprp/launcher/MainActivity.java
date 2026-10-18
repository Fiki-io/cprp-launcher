package com.cprp.launcher;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvLogOutput;
    private ScrollView scrollLog;
    private Button btnTestVersion;
    private Button btnTestFlags;
    private Button btnTestHelp;
    private Button btnClearLog;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        checkCoreStatus();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvLogOutput = findViewById(R.id.tvLogOutput);
        scrollLog = findViewById(R.id.scrollLog);
        btnTestVersion = findViewById(R.id.btnTestVersion);
        btnTestFlags = findViewById(R.id.btnTestFlags);
        btnTestHelp = findViewById(R.id.btnTestHelp);
        btnClearLog = findViewById(R.id.btnClearLog);
    }

    private void setupListeners() {
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

    private void checkCoreStatus() {
        if (NativeBridge.isLoaded()) {
            tvStatus.setText(getString(R.string.status_ready));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_success));
            appendLog(getString(R.string.status_ready) + " [" + NativeBridge.getLoadedCore() + "]");
        } else {
            tvStatus.setText(getString(R.string.status_failed));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error));
            appendLog("Error loading core: " + NativeBridge.getLoadError());
            btnTestVersion.setEnabled(false);
            btnTestFlags.setEnabled(false);
            btnTestHelp.setEnabled(false);
        }
    }

    private void runCoreCommand(final String[] args) {
        if (!NativeBridge.isLoaded()) {
            appendLog(getString(R.string.status_failed));
            return;
        }

        if (android.os.Process.is64Bit() && args.length > 0 && "box86".equals(args[0])) {
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
                    Process process = Runtime.getRuntime().exec("logcat -d -v time -s CPRP_CORE:V CPRP_CORE64:V BOX86:V BOX64:V");
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
