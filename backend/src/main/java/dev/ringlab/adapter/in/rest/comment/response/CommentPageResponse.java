package dev.ringlab.adapter.in.rest.comment.response;

import java.util.List;

public record CommentPageResponse(
    List<CommentResponse> items, long total, int page, int size) {}
