package com.example.library.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookRequest(
    @NotBlank @Size(max = 160) String title,
    @Size(max = 120) String author
) {}
