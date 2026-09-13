package dev.ringlab.adapter.in.rest.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleSignInRequest(@NotBlank @Size(max = 16384) String credential) {}
