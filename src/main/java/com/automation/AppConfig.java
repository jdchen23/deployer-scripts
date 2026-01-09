package com.automation;

public class AppConfig {
    // ==========================================
    // CREDENTIALS
    // ==========================================
    public static final String HOST = "your.remote.server.com";
    public static final String USER = "your_username";
    public static final String PASSWORD = "your_password";

    // ==========================================
    // LOCAL PATHS (Windows)
    // ==========================================
    // Note: Use double backslashes for Windows paths in Java strings
    public static final String LOCAL_JAR_PATH = "C:\\maven-projects\\ajax-poller\\target\\ajax-poller-1.0.0-SNAPSHOT.jar";
    public static final String LOCAL_LOG_DEST = "C:\\maven-projects\\ajax-poller\\logs\\server.log";

    // ==========================================
    // REMOTE PATHS (Linux)
    // ==========================================
    public static final String REMOTE_TMP_DIR = "/tmp/usr1/";
    public static final String REMOTE_DEPLOY_DIR = "/opt/server/default/deploy/"; 
    public static final String REMOTE_JAR_NAME = "ajax-poller-1.0.0-SNAPSHOT.jar";
    public static final String REMOTE_LOG_PATH = "/opt/server/log/server.log"; 
    public static final String RESTART_SCRIPT = "/opt/server/bin/restart.sh";
    
    // ==========================================
    // COMMANDS
    // ==========================================
    public static final String PBRUN_CMD = "pbrun /bin/bash"; 
}
