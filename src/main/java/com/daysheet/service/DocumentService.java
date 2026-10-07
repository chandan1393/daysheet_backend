package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.AppUser;
import com.daysheet.domain.Client;
import com.daysheet.domain.ClientDocument;
import com.daysheet.dto.DocumentDtos.DocumentDto;
import com.daysheet.dto.DocumentDtos.FileDownload;
import com.daysheet.repository.AppUserRepository;
import com.daysheet.repository.ClientDocumentRepository;
import com.daysheet.security.CurrentUser;
import com.daysheet.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/** Prescriptions, reports, scans, contracts, worksheets and photos kept on a person's record. */
@Service
@RequiredArgsConstructor
public class DocumentService {

    /** Allowed file types. The content type is decided here, never trusted from the browser. */
    private static final Map<String, String> TYPES = Map.ofEntries(
            entry("pdf", "application/pdf"),
            entry("jpg", "image/jpeg"), entry("jpeg", "image/jpeg"), entry("png", "image/png"),
            entry("webp", "image/webp"), entry("gif", "image/gif"),
            entry("heic", "image/heic"), entry("heif", "image/heif"),
            entry("doc", "application/msword"),
            entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            entry("xls", "application/vnd.ms-excel"),
            entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            entry("txt", "text/plain"), entry("csv", "text/csv"));

    /** Types a browser can show safely in a preview. Everything else downloads. */
    private static final Set<String> INLINE = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp", "image/gif");

    private final ClientDocumentRepository documents;
    private final AppUserRepository users;
    private final ClientService clientService;
    private final FileStorage storage;

    @Value("${app.storage.quota-mb-per-practice}")
    private long quotaMb;

    @Transactional
    public DocumentDto upload(Long clientId, MultipartFile file, Long appointmentId) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to upload.");
        Client client = clientService.find(clientId);
        String name = cleanName(file.getOriginalFilename());
        String type = TYPES.get(extension(name));
        if (type == null) {
            throw ApiException.badRequest("Upload a PDF, photo, Word, Excel or text file.");
        }

        long used = documents.totalBytes(client.getWorkspace().getId());
        if (used + file.getSize() > quotaMb * 1024 * 1024) {
            throw ApiException.badRequest("Your storage is full (" + (quotaMb / 1024.0 >= 1 ? (quotaMb / 1024) + " GB" : quotaMb + " MB")
                    + "). Delete old files or email us to add more space.");
        }

        ClientDocument doc = new ClientDocument();
        doc.setWorkspace(client.getWorkspace());
        doc.setClient(client);
        doc.setAppointment(clientService.visitOf(client, appointmentId));
        doc.setFileName(name);
        doc.setContentType(type);
        doc.setSizeBytes(file.getSize());
        doc.setUploadedBy(users.findById(CurrentUser.userId()).map(AppUser::getFullName).orElse(null));

        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.save(client.getWorkspace().getId(), in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the uploaded file", e);
        }
        doc.setStorageKey(key);
        // If the database save fails, don't leave an orphaned file behind.
        afterRollback(() -> storage.delete(key));
        return Mappers.document(documents.save(doc));
    }

    @Transactional(readOnly = true)
    public FileDownload download(Long id) {
        ClientDocument d = find(id);
        Resource resource = storage.load(d.getStorageKey());
        if (!resource.exists()) throw ApiException.notFound("File");
        return new FileDownload(resource, d.getFileName(), d.getContentType(), d.getSizeBytes(),
                INLINE.contains(d.getContentType()));
    }

    @Transactional
    public void delete(Long id) {
        ClientDocument d = find(id);
        String key = d.getStorageKey();
        documents.delete(d);
        afterCommit(() -> storage.delete(key));
    }

    private ClientDocument find(Long id) {
        return documents.findByIdAndWorkspaceId(id, CurrentUser.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Document"));
    }

    private static String cleanName(String original) {
        String name = original == null ? "file" : original.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\t]", " ").trim();
        if (name.isEmpty()) name = "file";
        return name.length() > 180 ? name.substring(name.length() - 180) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { task.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { task.run(); }
        });
    }

    private static void afterRollback(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) task.run();
            }
        });
    }
}
