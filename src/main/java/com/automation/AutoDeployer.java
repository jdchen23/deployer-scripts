package com.automation;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

public class AutoDeployer {

    private static final Logger logger = LoggerFactory.getLogger(AutoDeployer.class);

    public static void main(String[] args) {
        Session session = null;
        ChannelSftp sftpChannel = null;
        ChannelShell shellChannel = null;

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
            // STEP 1: Upload File via SFTP
            // ---------------------------------------------------------
            logger.info("Starting SFTP Upload...");
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            
            logger.info("Uploading local file: {}", AppConfig.LOCAL_JAR_PATH);
            sftpChannel.put(AppConfig.LOCAL_JAR_PATH, AppConfig.REMOTE_TMP_DIR + AppConfig.REMOTE_JAR_NAME);
            logger.info("Upload complete.");
            sftpChannel.disconnect();

            // ---------------------------------------------------------
            // STEP 2: Deploy & Restart via Shell (pbrun)
            // ---------------------------------------------------------
            logger.info("Initializing Shell for pbrun execution...");
            shellChannel = (ChannelShell) session.openChannel("shell");
            
            OutputStream inputToServer = shellChannel.getOutputStream();
            InputStream outputFromServer = shellChannel.getInputStream();
            shellChannel.connect();

            PrintWriter writer = new PrintWriter(inputToServer);

            // A. Elevate
            logger.info("Executing pbrun command: {}", AppConfig.PBRUN_CMD);
            writer.println(AppConfig.PBRUN_CMD);
            writer.flush();
            Thread.sleep(2000); 

            // B. Cleanup old jar
            logger.info("Removing old jar versions...");
            writer.println("rm " + AppConfig.REMOTE_DEPLOY_DIR + "ajax-poller-*.jar");
            
            // C. Move new jar
            logger.info("Moving new jar to deployment folder...");
            writer.println("cp " + AppConfig.REMOTE_TMP_DIR + AppConfig.REMOTE_JAR_NAME + " " + AppConfig.REMOTE_DEPLOY_DIR);
            
            // D. Restart
            logger.info("Executing restart script: {}", AppConfig.RESTART_SCRIPT);
            writer.println("sh " + AppConfig.RESTART_SCRIPT);
            
            // E. Exit
            writer.println("exit"); // exit pbrun
            writer.println("exit"); // exit ssh shell
            writer.flush();

            logger.info("Commands sent. Waiting for remote output...");

            // Monitor Output
            byte[] tmp = new byte[1024];
            while (true) {
                while (outputFromServer.available() > 0) {
                    int i = outputFromServer.read(tmp, 0, 1024);
                    if (i < 0) break;
                    System.out.print(new String(tmp, 0, i));
                }
                if (shellChannel.isClosed()) {
                    logger.info("Shell session closed. Exit code: {}", shellChannel.getExitStatus());
                    break;
                }
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            logger.error("Deployment process failed", e);
        } finally {
            if (sftpChannel != null) sftpChannel.disconnect();
            if (shellChannel != null) shellChannel.disconnect();
            if (session != null) session.disconnect();
        }
    }
}
