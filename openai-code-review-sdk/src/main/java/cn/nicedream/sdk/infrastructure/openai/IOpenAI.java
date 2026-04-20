package cn.nicedream.sdk.infrastructure.openai;


import cn.nicedream.sdk.infrastructure.openai.dto.ChatCompletionRequestDTO;
import cn.nicedream.sdk.infrastructure.openai.dto.ChatCompletionSyncResponseDTO;

public interface IOpenAI {

    ChatCompletionSyncResponseDTO completions(ChatCompletionRequestDTO requestDTO) throws Exception;

}
