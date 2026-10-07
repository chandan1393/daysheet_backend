package com.daysheet.web;

import com.daysheet.dto.ClientDtos.*;
import com.daysheet.dto.DocumentDtos.DocumentDto;
import com.daysheet.service.ClientService;
import com.daysheet.service.DocumentService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clients;
    private final DocumentService documents;

    @GetMapping
    public List<ClientSummary> list(@RequestParam(required = false) String q) { return clients.list(q); }

    @GetMapping("/{id}")
    public ClientDetail get(@PathVariable Long id) { return clients.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientSummary create(@Valid @RequestBody ClientRequest request) { return clients.create(request); }

    @PutMapping("/{id}")
    public ClientDetail update(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clients.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable Long id) { clients.archive(id); }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteDto addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return clients.addNote(id, request);
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNote(@PathVariable Long id, @PathVariable Long noteId) { clients.deleteNote(id, noteId); }

    /** Upload a prescription, report, scan or any file. Pass appointmentId to attach it to a visit. */
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto upload(@PathVariable Long id,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(required = false) Long appointmentId) {
        return documents.upload(id, file, appointmentId);
    }
}
