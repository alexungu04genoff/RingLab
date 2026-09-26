package dev.ringlab.adapter.in.rest.build.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.ringlab.adapter.in.rest.gamedata.response.BuildStatsResponse;

public record BuildPageResponse(List<BuildResponse> items, long total, int page, int size,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<UUID, BuildStatsResponse> statsByBuildId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String statsError) {}
