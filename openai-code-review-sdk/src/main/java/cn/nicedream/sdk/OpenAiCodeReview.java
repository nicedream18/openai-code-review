package cn.nicedream.sdk;

import cn.nicedream.sdk.domain.model.ChatCompletionRequest;
import cn.nicedream.sdk.domain.model.ChatCompletionSyncResponse;
import cn.nicedream.sdk.domain.model.Model;
import cn.nicedream.sdk.type.utils.BearerTokenUtils;
import cn.nicedream.sdk.type.utils.WXAccessTokenUtils;
import com.alibaba.fastjson2.JSON;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class OpenAiCodeReview {
    private static final String API_KEY_SECRET = "201d073da3e043369d269f677726bc86.tushBH9GlzPZTubn";
    private static final String LOG_REPO_URL = "https://github.com/nicedream18/openai-code-review-log.git";
    private static final String LOG_REPO_WEB_URL = "https://github.com/nicedream18/openai-code-review-log";
    private static final String DEFAULT_BRANCH = "main";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String CODE_REVIEW_PROMPT = "你是一个高级编程架构师，精通各类场景方案、架构设计和编程语言请您根据git diff记录，对代码做出评审。代码如下:\n\n";

    public static void main(String[] args) throws Exception {
        System.out.println("openai代码评审,测试运行");
        String token = System.getenv("GITHUB_TOKEN");
        if(null == token|| token.isEmpty()){
            throw new RuntimeException("token is null");
        }
        // 1. 代码检出
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

        System.out.println("diff code：" + diffCode.toString());

        // 2. chatglm 代码评审
        String log = codeReview(diffCode.toString());
        System.out.println("code review：" + log);

        //3. 写入评审日志
        String logUrl = writeLog(token, log);
        System.out.println("writeLog：" + logUrl);

        // 4. 消息通知
        System.out.println("pushMessage：" + logUrl);
        pushMessage(logUrl);

    }

    private static void pushMessage(String logUrl) {
        String accessToken =  WXAccessTokenUtils.getAccessToken();
        System.out.println(accessToken);

        Message message = new Message();
        message.put("project", "pay-mall");
        message.put("review", logUrl);
        message.setUrl(logUrl);
        message.setTemplate_id("TvNQ7M1G7WjEW4pTtRR65aLa2u6vLAQXr8SrDOVAON0");

        String url = String.format("https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=%s", accessToken);
        sendPostRequest(url, JSON.toJSONString(message));
    }

    private static void sendPostRequest(String urlString, String jsonBody) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
                String response = scanner.useDelimiter("\\A").next();
                System.out.println(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static class Message {
        private String touser = "om1eY2BfA1Bw4ADmrlMBihhCKJk4";
        private String template_id = "TvNQ7M1G7WjEW4pTtRR65aLa2u6vLAQXr8SrDOVAON0";
        private String url = "https://github.com/nicedream18/openai-code-review-log/blob/main/2026-04-19/WFAqwBAhWwU1.md";
        private Map<String, Map<String, String>> data = new HashMap<>();

        public void put(String key, String value) {
            data.put(key, new HashMap<String, String>() {
                {
                    put("value", value);
                }
            });
        }

        public String getTouser() {
            return touser;
        }

        public void setTouser(String touser) {
            this.touser = touser;
        }

        public String getTemplate_id() {
            return template_id;
        }

        public void setTemplate_id(String template_id) {
            this.template_id = template_id;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Map<String, Map<String, String>> getData() {
            return data;
        }

        public void setData(Map<String, Map<String, String>> data) {
            this.data = data;
        }
    }

    private static String codeReview(String diffCode) throws IOException {
        String apiKeySecret = API_KEY_SECRET;
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

    private static String writeLog(String token, String log) throws Exception {
        String branch = System.getenv("LOG_REPO_BRANCH");
        if (branch == null || branch.isEmpty()) {
            branch = DEFAULT_BRANCH;
        }

        Git git = null;
        try {
            git = Git.cloneRepository()
                    .setURI(LOG_REPO_URL)
                    .setBranch("refs/heads/" + branch)
                    .setDirectory(new File("repo"))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .call();

            String dateFolderName = new SimpleDateFormat(DATE_FORMAT).format(new Date());
            File dateFolder = new File("repo/" + dateFolderName);
            if (!dateFolder.exists()) {
                dateFolder.mkdirs();
            }

            String fileName = generateRandomString(12) + ".md";
            File newFile = new File(dateFolder, fileName);
            try (FileWriter writer = new FileWriter(newFile)) {
                writer.write(log);
            }

            git.add().addFilepattern(dateFolderName + "/" + fileName).call();
            git.commit().setMessage("Add code review log: " + fileName).call();
            git.push()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .call();

            return LOG_REPO_WEB_URL + "/blob/" + branch + "/" + dateFolderName + "/" + fileName;
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

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
