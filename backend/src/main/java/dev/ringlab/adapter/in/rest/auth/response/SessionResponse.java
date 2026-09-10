package dev.ringlab.adapter.in.rest.auth.response;

public record SessionResponse(String token, UserResponse user) {}
