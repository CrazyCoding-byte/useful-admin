package com.yzx.filestorage.config;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Description:
 *
 * @author: yzx
 * @create: 2024-12-29 11:47
 **/

@Component
public class OkHttpUtils {
    private static final String MIME_JSON = "application/json; charset=utf-8";
    @Autowired
    @Qualifier("okHttpClientWithProxy")
    private OkHttpClient httpClient;

    // GET-无参
    public Response get(String url) throws IOException {
        return basicGet(url, null);
    }

    // GET-有参
    public Response get(String url, Map<String, String> paramMap) throws IOException {
        return get(url, paramMap, null);
    }

    // GET-有参、有请求头
    public Response get(String url, Map<String, String> paramMap, Map<String, String> headers) throws IOException {
        StringBuilder sb = new StringBuilder(url);
        if (paramMap != null && !paramMap.isEmpty()) {
            Set<String> keySet = paramMap.keySet();
            for (String key : keySet) {
                sb.append(sb.toString().contains("?") ? "&" : "?");
                sb.append(key).append("=").append(paramMap.get(key));
            }
            url = sb.toString();
        }
        return basicGet(url, headers);
    }

    public Response post(String url) throws IOException {
        return post(url, null, null);
    }

    public Response post(String url, String reqBodyJson) throws IOException {
        return post(url, reqBodyJson, null);
    }

    /**
     * 发送post请求
     *
     * @param url         请求路径
     * @param reqBodyJson 请求体JSON格式
     * @param headers     请求头
     * @return java.lang.String
     * @author aoxiang
     * @create 2024/12/29
     **/
    public Response post(String url, String reqBodyJson, Map<String, String> headers) throws IOException {
        MediaType mediaType = MediaType.Companion.parse(MIME_JSON);
        RequestBody requestBody;

        if (reqBodyJson == null) {
            requestBody = RequestBody.Companion.create("", mediaType);
        } else {
            requestBody = RequestBody.Companion.create(reqBodyJson, mediaType);
        }
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Accept", MIME_JSON)
                .post(requestBody);

        if (headers != null && !headers.isEmpty()) {
            headers.forEach(requestBuilder::header);
        }

        return httpClient.newCall(requestBuilder.build()).execute();
    }

    private Response basicGet(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .get();
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return httpClient.newCall(builder.build()).execute();
    }
}
