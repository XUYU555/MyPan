package com.xxyy.utils;

import com.xxyy.utils.common.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
            // 处理执行cmd命令返回结果
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
                        inputStringBuffer.append(inputLine);
                    }
                    logger.info("视频转化完成命令为{},结果为{}", command, inputStringBuffer);
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

            // 处理执行失败，异常输出
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
                        errorStringBuffer.append(errorLine);
                    }
                    logger.info("视频转化失败命令为{},结果为{}", command, errorStringBuffer);
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

            // 这里进程阻塞，将等待外部转换成功后，才往下执行
            process.waitFor();
            errorThread.join();
            inputThread.join();

            if (inputStringBuffer.length() > 0) {
                return inputStringBuffer.toString();
            } else {
                return errorStringBuffer.toString();
            }
        } catch (IOException e) {
            logger.error("IO流错误", e);
            throw new AppException("cmd命令执行失败");
        } catch (InterruptedException e) {
            logger.error("线程阻塞错误", e);
            throw new AppException("cmd命令执行失败");
        }

    }
}
