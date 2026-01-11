// java
package com.automation;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

public class AutoDeployer {

    private static final Logger logger = LoggerFactory.getLogger(AutoDeployer.class);

    public static void main(String[] args) {
        if (args.length == 0) {
            // If no flags provided, default to deploy then restart
            try {
                String localFile = AppConfig.LOCAL_JAR_PATH;
                deploy(localFile);
                restart();
            } catch (Exception e) {
                logger.error("Operation failed", e);
            }
            return;
        }

        String op = null;
        String file = null;

        // Simple flag parser: accepts -o <operation> and -f <file> in any order
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-o".equals(a) && i + 1 < args.length) {
                op = args[++i].toLowerCase();
            } else if ("-f".equals(a) && i + 1 < args.length) {
                file = args[++i];
            } else {
                logger.warn("Unknown token or missing value: {}", a);
            }
        }

        try {
            if (op == null) {
                // treat as deploy then restart
                String localFile = (file != null) ? file : AppConfig.LOCAL_JAR_PATH;
                deploy(localFile);
                restart();
                return;
            }

            switch (op) {
                case "deploy": {
                    String localFile = (file != null) ? file : AppConfig.LOCAL_JAR_PATH;
                    deploy(localFile);
                    break;
                }
                case "restart":
                    restart();
                    break;
                default:
                    logger.error("Unknown operation: {}", op);
                    printUsage();
            }
        } catch (Exception e) {
            logger.error("Operation failed", e);
        }
    }

    private static void printUsage() {
        logger.info("Usage: java -jar `AutoDeployer.jar` [-o <deploy|restart>] [-f <local-file>]");
        logger.info("If `-o` is omitted the tool will perform deploy then restart (use `-f` to specify the file).");
        logger.info("Examples:");
        logger.info("  - deploy a specific file: -o deploy -f path/to/file.jar");
        logger.info("  - restart only:          -o restart");
        logger.info("  - deploy then restart (no -o): (no flags) or -f path/to/file.jar");
    }

    /**
     * Uploads the given local file via SFTP to the remote temp directory, then runs pbrun
     * to remove old jars and copy the uploaded file into the deploy folder.
     */
    public static void deploy(String localFilePath) throws Exception {
        Session session = null;
        ChannelSftp sftpChannel = null;
        ChannelShell shellChannel = null;

        File localFile = new File(localFilePath);
        if (!localFile.exists() || !localFile.isFile()) {
            throw new FileNotFoundException("Local file not found: " + localFilePath);
        }
        String remoteJarName = localFile.getName();

        try {
            session = createSession();
            session.connect();
            logger.info("SSH Connection established for deploy to {}.", AppConfig.HOST);

            // SFTP upload
            logger.info("Starting SFTP upload: {} -> {}{}", localFilePath, AppConfig.REMOTE_TMP_DIR, remoteJarName);
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            sftpChannel.put(localFilePath, AppConfig.REMOTE_TMP_DIR + remoteJarName);
            logger.info("Upload complete.");
            sftpChannel.disconnect();

            // Shell: pbrun, remove old jars (pattern) and copy uploaded jar into deploy dir
            shellChannel = (ChannelShell) session.openChannel("shell");
            OutputStream inputToServer = shellChannel.getOutputStream();
            InputStream outputFromServer = shellChannel.getInputStream();
            shellChannel.connect();

            PrintWriter writer = new PrintWriter(inputToServer);

            logger.info("Executing pbrun command: {}", AppConfig.PBRUN_CMD);
            writer.println(AppConfig.PBRUN_CMD);
            writer.flush();
            Thread.sleep(1500);

            logger.info("Removing old jar versions (pattern: ajax-poller-*.jar)...");
            writer.println("rm " + AppConfig.REMOTE_DEPLOY_DIR + "ajax-poller-*.jar");

            logger.info("Copying uploaded jar into deploy folder: {} -> {}", AppConfig.REMOTE_TMP_DIR + remoteJarName, AppConfig.REMOTE_DEPLOY_DIR);
            writer.println("cp " + AppConfig.REMOTE_TMP_DIR + remoteJarName + " " + AppConfig.REMOTE_DEPLOY_DIR);

            writer.println("exit"); // exit pbrun
            writer.println("exit"); // exit shell
            writer.flush();

            logger.info("Deploy commands sent. Waiting for remote output...");
            readAndLogOutput(outputFromServer, shellChannel);

        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) sftpChannel.disconnect();
            if (shellChannel != null && shellChannel.isConnected()) shellChannel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
            logger.info("Deploy session closed.");
        }
    }

    /**
     * Only restarts the remote service by invoking the restart script via pbrun.
     */
    public static void restart() throws Exception {
        Session session = null;
        ChannelShell shellChannel = null;

        try {
            session = createSession();
            session.connect();
            logger.info("SSH Connection established for restart to {}.", AppConfig.HOST);

            shellChannel = (ChannelShell) session.openChannel("shell");
            OutputStream inputToServer = shellChannel.getOutputStream();
            InputStream outputFromServer = shellChannel.getInputStream();
            shellChannel.connect();

            PrintWriter writer = new PrintWriter(inputToServer);

            writer.println(AppConfig.PBRUN_CMD);
            writer.flush();
            Thread.sleep(1500);

            logger.info("Executing restart script: {}", AppConfig.RESTART_SCRIPT);
            writer.println("sh " + AppConfig.RESTART_SCRIPT);

            writer.println("exit"); // exit pbrun
            writer.println("exit"); // exit shell
            writer.flush();

            logger.info("Restart commands sent. Waiting for remote output...");
            readAndLogOutput(outputFromServer, shellChannel);

        } finally {
            if (shellChannel != null && shellChannel.isConnected()) shellChannel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
            logger.info("Restart session closed.");
        }
    }

    private static Session createSession() throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(AppConfig.USER, AppConfig.HOST, 22);
        session.setPassword(AppConfig.PASSWORD);

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);

        return session;
    }

    private static void readAndLogOutput(InputStream outputFromServer, ChannelShell shellChannel) throws IOException, InterruptedException {
        byte[] tmp = new byte[1024];
        while (true) {
            while (outputFromServer.available() > 0) {
                int i = outputFromServer.read(tmp, 0, tmp.length);
                if (i < 0) break;
                System.out.print(new String(tmp, 0, i));
            }
            if (shellChannel.isClosed()) {
                logger.info("Shell session closed. Exit code: {}", shellChannel.getExitStatus());
                break;
            }
            Thread.sleep(500);
        }
    }
}
