package dev.ringlab.adapter.in.rest.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,30}") String username,
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull @Size(min = 8, max = 72) String password) {}
