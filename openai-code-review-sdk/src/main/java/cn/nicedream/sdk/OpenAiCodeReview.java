package cn.nicedream.sdk;


import cn.nicedream.sdk.domain.service.impl.OpenAiCodeReviewService;
import cn.nicedream.sdk.infrastructure.git.GitCommand;
import cn.nicedream.sdk.infrastructure.openai.IOpenAI;
import cn.nicedream.sdk.infrastructure.openai.impl.ChatGLM;
import cn.nicedream.sdk.infrastructure.weixin.WeiXin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAiCodeReview {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiCodeReview.class);

    // 配置配置
    private String weixin_appid = "wx00aea9e2410b2fcc";
    private String weixin_secret = "137ec64c0409937615af8ac9ab2fb6f5";
    private String weixin_touser = "om1eY2BfA1Bw4ADmrlMBihhCKJk4";
    private String weixin_template_id = "VJBSd6ksZooVW0ZVqbis1N4i_uhz36ykkV9_52JdGz8";

    // ChatGLM 配置
    private String chatglm_apiHost = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private String chatglm_apiKeySecret = "";

    //Github 配置
    private String github_review_log_uri;
    private String github_token;

    // 工程配置 - 自动获取
    private String github_project;
    private String github_branch;
    private String github_author;

    public static void main(String[] args) throws Exception {
        GitCommand gitCommand = new GitCommand(
                getEnv("GITHUB_REVIEW_LOG_URI"),
                getEnv("GITHUB_TOKEN"),
                getEnv("COMMIT_PROJECT"),
                getEnv("COMMIT_BRANCH"),
                getEnv("COMMIT_AUTHOR"),
                getEnv("COMMIT_MESSAGE")
        );

        /**
         * 项目：{{repo_name.DATA}} 分支：{{branch_name.DATA}} 作者：{{commit_author.DATA}} 说明：{{commit_message.DATA}}
         */
        WeiXin weiXin = new WeiXin(
                getEnv("WEIXIN_APPID"),
                getEnv("WEIXIN_SECRET"),
                getEnv("WEIXIN_TOUSER"),
                getEnv("WEIXIN_TEMPLATE_ID")
        );



        IOpenAI openAI = new ChatGLM(getEnv("CHATGLM_APIHOST"), getEnv("CHATGLM_APIKEYSECRET"));

        OpenAiCodeReviewService openAiCodeReviewService = new OpenAiCodeReviewService(gitCommand, openAI, weiXin);
        openAiCodeReviewService.exec();

        logger.info("openai-code-review done!");
    }

    private static String getEnv(String key) {
        String value = System.getenv(key);
        if (null == value || value.isEmpty()) {
            throw new RuntimeException("value is null");
        }
        return value;
    }


}
