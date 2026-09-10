package dev.ringlab.adapter.in.rest.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 30) String username, @NotBlank @Size(max = 72) String password) {}
