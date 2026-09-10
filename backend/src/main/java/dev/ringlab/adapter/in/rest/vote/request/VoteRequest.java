package dev.ringlab.adapter.in.rest.vote.request;

import jakarta.validation.constraints.NotNull;

public record VoteRequest(@NotNull Integer value) {}
