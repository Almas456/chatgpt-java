package com.plexpt.chatgpt.listener;

import com.alibaba.fastjson.JSON;
import com.plexpt.chatgpt.entity.chat.ChatChoice;
import com.plexpt.chatgpt.entity.chat.ChatCompletionResponse;
import com.plexpt.chatgpt.entity.chat.Message;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * EventSource listener for chat-related events.
 */
@Slf4j
public abstract class AbstractStreamListener extends EventSourceListener {

    protected String lastMessage = "";
    @Setter
    @Getter
    protected Consumer<String> onComplete = message -> {};

    /**
     * Called when a new message is received.
     * 
     * @param message the new message
     */
    public abstract void onMessageReceived(String message);

    /**
     * Called when an error occurs.
     * 
     * @param throwable the throwable that caused the error
     * @param response the response associated with the error, if any
     */
    public abstract void onError(Throwable throwable, String response);

    @Override
    public void onOpen(EventSource eventSource, Response response) {
        // do nothing
    }

    @Override
    public void onClosed(EventSource eventSource) {
        // do nothing
    }

    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        if (data.equals("[DONE]")) {
            log.info("Chat session completed: {}", lastMessage);
            onComplete.accept(lastMessage);
            return;
        }

        ChatCompletionResponse completionResponse = JSON.parseObject(data, ChatCompletionResponse.class);
        List<ChatChoice> choices = completionResponse.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }
        Message delta = choices.get(0).getDelta();
        String text = delta.getContent();

        if (text != null) {
            lastMessage += text;
            onMessageReceived(text);
        }
    }

    @SneakyThrows
    @Override
    public void onFailure(EventSource eventSource, Throwable throwable, Response response) {
        try {
            log.error("Stream connection error: {}", throwable);

            String responseText = "";

            if (Objects.nonNull(response)) {
                responseText = response.body().string();
            }

            log.error("Response: {}", responseText);

            String forbiddenText = "Your access was terminated due to violation of our policies";

            if (responseText.contains(forbiddenText)) {
                log.error("Chat session has been terminated due to policy violation");
            }

            onError(throwable, responseText);
        } catch (Exception e) {
            // do nothing
        } finally {
            eventSource.cancel();
        }
    }
}
