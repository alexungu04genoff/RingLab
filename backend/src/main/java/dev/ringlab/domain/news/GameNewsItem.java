package dev.ringlab.domain.news;

import java.time.Instant;

public record GameNewsItem(String id, String title, String url, Instant publishedAt) {}
