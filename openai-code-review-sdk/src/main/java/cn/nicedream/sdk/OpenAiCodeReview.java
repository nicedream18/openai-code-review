package cn.nicedream.sdk;

import cn.nicedream.sdk.domain.model.ChatCompletionRequest;
import cn.nicedream.sdk.domain.model.ChatCompletionSyncResponse;
import cn.nicedream.sdk.domain.model.Model;
import cn.nicedream.sdk.type.utils.BearerTokenUtils;
import com.alibaba.fastjson2.JSON;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

public class OpenAiCodeReview {
    // 常量定义
    private static final String API_KEY_ENV = "API_KEY_SECRET";
    private static final String LOG_REPO_URL = "https://github.com/nicedream18/openai-code-review-log.git";
    private static final String DEFAULT_BRANCH = "main";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String CODE_REVIEW_PROMPT = "你是一个高级编程架构师，精通各类场景方案、架构设计和编程语言请您根据git diff记录，对代码做出评审。代码如下:\n\n";
    
    public static void main(String[] args) {
        System.out.println("openai代码评审,测试运行");
        
        try {
            // 1. 获取必要的环境变量
            String githubToken = getRequiredEnv("GITHUB_TOKEN");
            
            // 2. 代码检出
            String diffCode = getGitDiff();
            System.out.println("diff code：" + diffCode);
            
            // 3. 执行代码评审
            String log = codeReview(diffCode);
            System.out.println("code review：" + log);
            
            // 4. 写入评审日志
            String logUrl = writeLog(githubToken, log);
            System.out.println("日志地址: " + logUrl);
            
        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取必需的环境变量
     */
    private static String getRequiredEnv(String envName) {
        String value = System.getenv(envName);
        if (value == null || value.isEmpty()) {
            throw new RuntimeException(envName + " 环境变量未设置");
        }
        return value;
    }
    
    /**
     * 获取Git差异
     */
    private static String getGitDiff() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("git", "diff", "HEAD~1", "HEAD");
        processBuilder.directory(new File("."));

        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        StringBuilder diffCode = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            diffCode.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        System.out.println("Exited with code:" + exitCode);
        
        return diffCode.toString();
    }
    
    /**
     * 执行代码评审
     */
    private static String codeReview(String diffCode) throws IOException {
        String apiKeySecret = getRequiredEnv(API_KEY_ENV);
        String token = BearerTokenUtils.getToken(apiKeySecret);

        URL url = new URL("https://open.bigmodel.cn/api/paas/v4/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setModel(Model.GLM_4_FLASH.getCode());
        chatCompletionRequest.setMessages(new ArrayList<ChatCompletionRequest.Prompt>() {
            private static final long serialVersionUID = -7988151926241837899L;

            {
                add(new ChatCompletionRequest.Prompt("user", CODE_REVIEW_PROMPT + diffCode));
            }
        });

        String requestBody = JSON.toJSONString(chatCompletionRequest);
        System.out.println("requestBody: " + requestBody);

        try(OutputStream os = connection.getOutputStream()){
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input);
        }

        int responseCode = connection.getResponseCode();
        System.out.println("HTTP responseCode: " + responseCode);

        InputStream inputStream;
        if (responseCode >= 400) {
            inputStream = connection.getErrorStream();
        } else {
            inputStream = connection.getInputStream();
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String inputLine;

        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null){
            content.append(inputLine);
        }

        in.close();
        connection.disconnect();

        String responseBody = content.toString();
        System.out.println("responseBody: " + responseBody);

        if (responseCode >= 400) {
            throw new RuntimeException("API调用失败, HTTP " + responseCode + ", 响应: " + responseBody);
        }

        System.out.println("评审结果："+responseBody);
        ChatCompletionSyncResponse response = JSON.parseObject(responseBody, ChatCompletionSyncResponse.class);
        return response.getChoices().get(0).getMessage().getContent();
    }

    /**
     * 写入评审日志
     */
    private static String writeLog(String token, String log) throws Exception {
        String branch = System.getenv("LOG_REPO_BRANCH");
        if (branch == null || branch.isEmpty()) {
            branch = DEFAULT_BRANCH;
        }

        Git git = null;
        try {
            // 克隆仓库
            git = Git.cloneRepository()
                    .setURI(LOG_REPO_URL)
                    .setDirectory(new File("repo"))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .call();

            // 创建日期目录
            String dateFolderName = new SimpleDateFormat(DATE_FORMAT).format(new Date());
            File dateFolder = new File("repo/" + dateFolderName);
            if (!dateFolder.exists()) {
                dateFolder.mkdirs();
            }

            // 生成文件名并写入日志
            String fileName = generateRandomString(12) + ".md";
            File newFile = new File(dateFolder, fileName);
            System.out.println("开始写日志...");
            System.out.println("日志文件路径: " + newFile.getAbsolutePath());
            
            try (FileWriter writer = new FileWriter(newFile)) {
                writer.write(log);
            }

            // 提交并推送
            git.add().addFilepattern(dateFolderName + "/" + fileName).call();
            git.commit().setMessage("Add code review log: " + fileName).call();
            System.out.println("开始 push...");
            git.push()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .call();
            System.out.println("push 完成");

            // 构建日志URL
            return "https://github.com/nicedream18/openai-code-review-log/blob/" + branch + "/" + dateFolderName + "/" + fileName;
        } finally {
            // 清理资源
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * 生成随机字符串
     */
    private static String generateRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }
}