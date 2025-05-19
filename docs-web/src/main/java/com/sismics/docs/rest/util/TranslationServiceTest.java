package com.sismics.docs.rest.util;

public class TranslationServiceTest {
    public static void main(String[] args) {
        TranslationService service = new TranslationService();
        String originalText = "Hello, world!";
        // 翻译为中文
        String translated = service.translate(originalText, "zh");
        System.out.println("原文: " + originalText);
        System.out.println("翻译结果: " + translated);
    }
}