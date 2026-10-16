package com.tothework.laborqa.web;

import jakarta.validation.constraints.NotBlank;

public record LaborQaRequest(@NotBlank String question) {
}
