package com.xxyy.utils;

import com.xxyy.utils.common.AppException;
import io.minio.GetObjectResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author xy
 * @date 2024-09-26 16:17
 * CMD工具类
 */
public class ProcessUtils {

    private final static Logger log = LoggerFactory.getLogger(ProcessUtils.class);


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
                    log.error("读取cmd命令失败", e);
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
                        log.error("关闭字节流失败", e);
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
                    log.error("读取cmd命令失败", e);
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
                        log.error("关闭字节流失败", e);
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
                log.error("命令执行失败，退出状态码为{}", exitCode);
                return errorStringBuffer.toString();
            }

            // 返回标准输出
            return inputStringBuffer.length() > 0 ? inputStringBuffer.toString() : errorStringBuffer.toString();
        } catch (IOException | InterruptedException e) {
            log.error("命令执行失败", e);
            throw new AppException("cmd命令执行失败");
        }
    }


    public static ByteArrayOutputStream streamCutCover(List<String> command, GetObjectResponse fileStream) throws Exception {
        ExecutorService threadPool = Executors.newFixedThreadPool(2);
        try {
            // ProcessUtils.executeCommand(command);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            // 开启线程从minIO中获得的输出流写入ffmpeg进程的标准输入流
            Future<?> inputFuture = threadPool.submit(() -> {
                try (OutputStream processIn = process.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fileStream.read(buffer)) != -1) {
                        processIn.write(buffer, 0, len);
                    }
                    processIn.flush();
                } catch (IOException e) {
                    log.error("写入FFmpeg标准输入失败：{}", e.getMessage(), e);
                    throw new AppException("生成缩略图失败", e);
                }
            });
            // 任务2：消费 FFmpeg 的错误输出流
            Future<?> errorFuture = threadPool.submit(() -> {
                try (InputStream errorStream = process.getErrorStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.warn("FFmpeg error: {}", line);
                    }
                } catch (IOException e) {
                    log.error("读取FFmpeg错误流失败：{}", e.getMessage(), e);
                }
            });
            // 从ffmpeg标准输出流中获得数据
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (InputStream processOut = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = processOut.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
            }

            // 等待输入线程和错误线程完成，并设置超时(防止阻塞)
            inputFuture.get(60, TimeUnit.SECONDS);
            errorFuture.get(60, TimeUnit.SECONDS);

            // 等待 FFmpeg 进程结束，设置超时避免阻塞
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new RuntimeException("FFmpeg 处理超时");
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg进程执行失败，退出码：" + exitCode);
            }
            return outputStream;
        } finally {
            threadPool.shutdown();
        }
    }

}
