package com.automation;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

public class LogRetriever {

    private static final Logger logger = LoggerFactory.getLogger(LogRetriever.class);

    public static void main(String[] args) {
        Session session = null;
        ChannelShell shellChannel = null;
        ChannelSftp sftpChannel = null;

        try {
            logger.info("Connecting to {}...", AppConfig.HOST);
            JSch jsch = new JSch();
            session = jsch.getSession(AppConfig.USER, AppConfig.HOST, 22);
            session.setPassword(AppConfig.PASSWORD);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            logger.info("SSH Connection established.");

            // ---------------------------------------------------------
            // STEP 3a: Prepare Log File
            // ---------------------------------------------------------
            logger.info("Preparing remote log file (Elevating to copy and chmod)...");
            shellChannel = (ChannelShell) session.openChannel("shell");
            OutputStream inputToServer = shellChannel.getOutputStream();
            shellChannel.connect();

            PrintWriter writer = new PrintWriter(inputToServer);

            // Elevate
            writer.println(AppConfig.PBRUN_CMD);
            writer.flush();
            Thread.sleep(1000); 

            // Copy and change permissions
            writer.println("cp " + AppConfig.REMOTE_LOG_PATH + " " + AppConfig.REMOTE_TMP_DIR);
            writer.println("chmod 755 " + AppConfig.REMOTE_TMP_DIR + "server.log");
            
            writer.println("exit");
            writer.println("exit");
            writer.flush();
            
            while (!shellChannel.isClosed()) {
                Thread.sleep(500);
            }
            shellChannel.disconnect();

            // ---------------------------------------------------------
            // STEP 3b: Download via SFTP
            // ---------------------------------------------------------
            logger.info("Downloading log file...");
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            sftpChannel.get(AppConfig.REMOTE_TMP_DIR + "server.log", AppConfig.LOCAL_LOG_DEST);
            
            logger.info("Log download successful. Saved to: {}", AppConfig.LOCAL_LOG_DEST);

        } catch (Exception e) {
            logger.error("Log retrieval failed", e);
        } finally {
            if (sftpChannel != null) sftpChannel.disconnect();
            if (shellChannel != null) shellChannel.disconnect();
            if (session != null) session.disconnect();
        }
    }
}
