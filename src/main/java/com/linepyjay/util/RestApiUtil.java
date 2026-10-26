package com.linepyjay.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * REST API utility for JSON-based LINE API calls.
 */
public class RestApiUtil {

    /**
     * Performs an HTTP POST with a JSON body.
     */
    public static String doPost(HttpClient httpClient, String url, String jsonBody,
                                 Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(url)).header("content-type", "application/json");
        headers.forEach(b::header);
        return httpClient.send(b.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
            HttpResponse.BodyHandlers.ofString()).body();
    }

    /**
     * Performs an HTTP GET.
     */
    public static String doGet(HttpClient httpClient, String url,
                                Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url));
        headers.forEach(b::header);
        return httpClient.send(b.GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }
}
