package dev.ringlab.adapter.in.rest.build.response;

import java.util.List;

public record BuildPageResponse(List<BuildResponse> items, long total, int page, int size) {}
