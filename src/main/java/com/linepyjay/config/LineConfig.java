package com.linepyjay.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Pattern;

@Configuration
public class LineConfig {

    public static final Pattern EMAIL_REGEX = Pattern.compile("[^@]+@[^@]+\\.[^@]+");
    public static final Pattern MID_REGEX   = Pattern.compile("u\\w{32}");
    public static final Pattern GID_REGEX   = Pattern.compile("c\\w{32}");

    @Value("${line.hostDomain:https://legy.line-apps.com}")
    private String hostDomain;

    @Value("${line.obsDomain:https://obs.line-apps.com}")
    private String obsDomain;

    @Value("${line.permitNoticePath:/acct/lp/lgn/sq/v1}")
    private String permitNoticePath;

    @Value("${line.authPath:/api/v3p/rs}")
    private String authPath;

    @Value("${line.talkPath:/api/v3/TalkService.do}")
    private String talkPath;

    @Value("${line.apiPath:/S4}")
    private String apiPath;

    @Value("${line.pollPath:/P4}")
    private String pollPath;

    @Value("${line.certPath:/Q}")
    private String certPath;

    @Value("${line.loginQrPath:/acct/lgn/sq/v1}")
    private String loginQrPath;

    @Value("${line.appVersion:14.0.1}")
    private String appVersion;

    @Value("${line.appName:IOSIPAD\t14.0.1\tiOS\t15.0}")
    private String lineAppName;

    @Value("${line.userAgent:Line/14.0.1}")
    private String userAgent;

    @Value("${line.systemName:iPhone}")
    private String systemName;

    @Value("${line.carrier:51089, 1-0}")
    private String carrier;

    @Value("${line.ipAddr:8.8.8.8}")
    private String ipAddr;

    @Value("${line.email:}")
    private String lineEmail;

    @Value("${line.password:}")
    private String linePassword;

    @Value("${line.authToken:}")
    private String authToken;

    @Value("${gemini.apiKey:}")
    private String geminiApiKey;

    @Value("${gemini.imageModel:gemini-2.0-flash-exp-image-generation}")
    private String geminiImageModel;

    @Value("${whatsapp.apiKey:}")
    private String whatsappApiKey;

    @Value("${whatsapp.numberFile:whatsapp_number.txt}")
    private String whatsappNumberFile;

    @Value("${broadcast.file:group_broadcast.txt}")
    private String broadcastFile;

    public String getHostDomain()         { return hostDomain; }
    public String getObsDomain()          { return obsDomain; }
    public String getAuthPath()           { return authPath; }
    public String getTalkPath()           { return talkPath; }
    public String getApiPath()            { return apiPath; }
    public String getPollPath()           { return pollPath; }
    public String getCertPath()           { return certPath; }
    public String getLoginQrPath()        { return loginQrPath; }
    public String getPermitNoticePath()   { return permitNoticePath; }
    public String getAppVersion()         { return appVersion; }
    public String getUserAgent()          { return userAgent; }
    public String getSystemName()         { return systemName; }
    public String getCarrier()            { return carrier; }
    public String getIpAddr()             { return ipAddr; }
    public String getLineEmail()          { return lineEmail; }
    public String getLinePassword()       { return linePassword; }
    public String getAuthToken()          { return authToken; }
    public String getGeminiApiKey()       { return geminiApiKey; }
    public String getGeminiImageModel()   { return geminiImageModel; }
    public String getWhatsappApiKey()     { return whatsappApiKey; }
    public String getWhatsappNumberFile() { return whatsappNumberFile; }
    public String getBroadcastFile()      { return broadcastFile; }

    public String getLineAppName() {
        return lineAppName.replace("\\t", "\t");
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}