package dev.ringlab.adapter.in.rest.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationRequest(@NotBlank @Size(max = 512) String token) {}
