package com.daysheet.dto;

import org.springframework.core.io.Resource;

import java.time.Instant;

public final class DocumentDtos {
    private DocumentDtos() {}

    public record DocumentDto(Long id, String fileName, String contentType, long sizeBytes,
                              Long appointmentId, String uploadedBy, Instant createdAt) {}

    /** What the controller needs to stream a file back. */
    public record FileDownload(Resource resource, String fileName, String contentType, long size, boolean inline) {}
}
