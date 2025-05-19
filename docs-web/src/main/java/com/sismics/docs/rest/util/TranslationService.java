package com.sismics.docs.rest.util;

import jakarta.json.*;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class TranslationService {
    // 从环境变量获取凭证（生产环境推荐方式）
    private static final String ACCESS_KEY    = System.getenv("ALIYUN_ACCESS_KEY");
    private static final String ACCESS_SECRET = System.getenv("ALIYUN_ACCESS_SECRET");
    
    // 阿里云API配置
    private static final String ENDPOINT = "mt.cn-hangzhou.aliyuncs.com";
    private static final String API_VERSION = "2018-10-12";
    private static final String SIGNATURE_METHOD = "HMAC-SHA1";
    private static final String SIGNATURE_VERSION = "1.0";

    /**
     * 执行文本翻译（中英互译）
     * @param text 待翻译文本
     * @param targetLang 目标语言 zh/en
     * @return 翻译结果（失败时返回原文）
     */
    public String translate(String text, String targetLang) {
        if (text == null || text.isEmpty()) return text;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Map<String, String> params = new TreeMap<>();
            params.put("Action", "TranslateGeneral");
            params.put("FormatType", "text");
            params.put("SourceLanguage", detectLanguage(text));
            params.put("TargetLanguage", targetLang);
            params.put("SourceText", text);
            params.put("Version", API_VERSION);
            params.put("AccessKeyId", ACCESS_KEY);
            params.put("Timestamp", generateTimestamp());
            params.put("SignatureMethod", SIGNATURE_METHOD);
            params.put("SignatureVersion", SIGNATURE_VERSION);
            params.put("SignatureNonce", UUID.randomUUID().toString());
            params.put("RegionId", "cn-hangzhou"); // 新增必填参数

            String signature = generateSignature(params);
            params.put("Signature", signature);

            // 构建GET请求URL
            String url = "https://" + ENDPOINT + "?" + buildQueryString(params);
            HttpGet httpGet = new HttpGet(url);

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                HttpEntity entity = response.getEntity();
                String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                System.out.println("翻译接口返回：" + result);
                return parseTranslationResult(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "翻译失败";
        }

    }

    // 以下为辅助方法（实际开发中可拆分为工具类）
    
    private String generateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private String detectLanguage(String text) {
        // 简单实现：非ASCII字符视为中文，否则视为英文
        return text.matches(".*[\\u4e00-\\u9fa5]+.*") ? "zh" : "en";
    }

    /**
     * 生成签名，只抛出 Mac/算法相关异常，不再抛出 UnsupportedEncodingException
     */
    private String generateSignature(Map<String, String> params) throws Exception {
        // 用 TreeMap 确保参数按字典序排序
        Map<String, String> sortedParams = new TreeMap<>(params);
        String canonicalizedQueryString = sortedParams.entrySet().stream()
                .map(e -> percentEncode(e.getKey()) + "=" + percentEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        
        String stringToSign = "GET&" + percentEncode("/") + "&" + percentEncode(canonicalizedQueryString);
        System.out.println("Generated stringToSign: " + stringToSign);
        
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((ACCESS_SECRET + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        // 使用 Base64 编码签名，而非 Hex 编码
        return Base64.getEncoder().encodeToString(signBytes);
    }

    private String percentEncode(String value) {
        if (value == null) return "";
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // 正常情况下不会发生
        }
    }


    private String buildQueryString(Map<String, String> params) {
        // 同样使用 TreeMap 确保排序与签名计算完全一致
        Map<String, String> sortedParams = new TreeMap<>(params);
        return sortedParams.entrySet().stream()
                .map(e -> percentEncode(e.getKey()) + "=" + percentEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String parseTranslationResult(String response) {
        response = response.trim();
        // 如果是 XML 格式
        if (response.startsWith("<?xml")) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                InputStream is = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
                Document doc = builder.parse(is);
                Element root = doc.getDocumentElement();
                String code = root.getElementsByTagName("Code").item(0).getTextContent();
                if ("200".equals(code)) {
                    return root.getElementsByTagName("Translated").item(0).getTextContent();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "[翻译失败]";
        } else {
            // 如果返回 JSON 格式，则采用 JSON 解析
            try (JsonReader reader = Json.createReader(new StringReader(response))) {
                JsonObject obj = reader.readObject();
                if (obj.getInt("Code", 500) == 200) {
                    return obj.getJsonObject("Data").getString("Translated");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "[翻译失败]";
        }
    }
}