package com.example.tonbo_app;

import java.util.ArrayList;
import java.util.List;

/**
 * 出行目的地解析結果
 * 用於 parseDestinationFromTravelPhrase 的返回值
 */
public class TravelParseResult {
    /** 解析出的目的地名稱（單一或已選最近候選） */
    public final String destination;
    /** 是否為多候選（如「天虹商場」有多家） */
    public final boolean isAmbiguous;
    /** 是否涉及跨境（用戶在內地，目的地為港澳台/境外） */
    public final boolean isCrossRegion;
    /** 候選列表（isAmbiguous 時使用，已按距離排序） */
    public final List<String> candidates;

    public TravelParseResult(String destination, boolean isAmbiguous, boolean isCrossRegion, List<String> candidates) {
        this.destination = destination;
        this.isAmbiguous = isAmbiguous;
        this.isCrossRegion = isCrossRegion;
        this.candidates = candidates != null ? new ArrayList<>(candidates) : new ArrayList<>();
    }

    public static TravelParseResult simple(String destination) {
        return new TravelParseResult(destination, false, false, null);
    }

    public static TravelParseResult ambiguous(String nearestDestination, List<String> candidates) {
        return new TravelParseResult(nearestDestination, true, false, candidates);
    }

    public static TravelParseResult crossRegion(String destination) {
        return new TravelParseResult(destination, false, true, null);
    }

    public boolean hasDestination() {
        return destination != null && !destination.trim().isEmpty();
    }
}
