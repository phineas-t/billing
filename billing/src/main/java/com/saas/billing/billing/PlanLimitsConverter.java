package com.saas.billing.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PlanLimitsConverter implements AttributeConverter<PlanLimits, String> {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    @Override
    public String convertToDatabaseColumn( PlanLimits limits) {
        if (limits == null) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(limits);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to convert PlanLimits to JSON", e);
        }
    }

    @Override
    public PlanLimits convertToEntityAttribute( String json) {
        if (json == null || json.isBlank()) {
            return PlanLimits.defaults();
        }
        try {
            return MAPPER.readValue(json, PlanLimits.class);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to convert JSON to PlanLimits", e);
        }
    }
}