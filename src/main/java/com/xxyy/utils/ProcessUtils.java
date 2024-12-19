package com.xxyy.utils;

import com.xxyy.utils.common.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author xy
 * @date 2024-09-26 16:17
 * CMD工具类
 */
public class ProcessUtils {

    private final static Logger logger = LoggerFactory.getLogger(ProcessUtils.class);

    public static String executeCommand(List<String> command) {
        StringBuffer inputStringBuffer = new StringBuffer();
        StringBuffer errorStringBuffer = new StringBuffer();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            // 处理标准输出
            Thread inputThread = new Thread(() -> {
                InputStream input = null;
                InputStreamReader reader = null;
                BufferedReader buffer = null;
                try {
                    input = process.getInputStream();
                    reader = new InputStreamReader(input);
                    buffer = new BufferedReader(reader);
                    String inputLine = "";
                    while((inputLine = buffer.readLine()) != null) {
                        inputStringBuffer.append(inputLine).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("读取cmd命令失败", e);
                    throw new AppException("读取命令失败");
                } finally {
                    try {
                        if (input != null) {
                            input.close();
                        }
                        if (reader != null) {
                            reader.close();
                        }
                        if (buffer != null) {
                            buffer.close();
                        }
                    } catch (IOException e) {
                        logger.error("关闭字节流失败", e);
                    }
                }
            });
            inputThread.setName("ffmpeg:inputStream");
            inputThread.start();

            // 处理错误输出
            Thread errorThread = new Thread(() -> {
                InputStream input = null;
                InputStreamReader reader = null;
                BufferedReader buffer = null;
                try {
                    input = process.getErrorStream();
                    reader = new InputStreamReader(input);
                    buffer = new BufferedReader(reader);
                    String errorLine = "";
                    while((errorLine = buffer.readLine()) != null) {
                        errorStringBuffer.append(errorLine).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("读取cmd命令失败", e);
                    throw new AppException("读取命令失败");
                } finally {
                    try {
                        if (input != null) {
                            input.close();
                        }
                        if (reader != null) {
                            reader.close();
                        }
                        if (buffer != null) {
                            buffer.close();
                        }
                    } catch (IOException e) {
                        logger.error("关闭字节流失败", e);
                    }
                }
            });
            errorThread.setName("ffmpeg:error");
            errorThread.start();

            // 等待进程结束并获取退出状态
            process.waitFor();
            inputThread.join();
            errorThread.join();

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                // 如果命令失败，将错误信息返回
                logger.error("命令执行失败，退出状态码为{}", exitCode);
                return errorStringBuffer.toString();
            }

            // 返回标准输出
            return inputStringBuffer.length() > 0 ? inputStringBuffer.toString() : errorStringBuffer.toString();
        } catch (IOException | InterruptedException e) {
            logger.error("命令执行失败", e);
            throw new AppException("cmd命令执行失败");
        }
    }

}
