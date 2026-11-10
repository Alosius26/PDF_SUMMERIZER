package Alosius.PDF.Summarize.service;

import Alosius.PDF.Summarize.dto.SummaryResponse;
import Alosius.PDF.Summarize.entity.SummaryBulletPoint;
import Alosius.PDF.Summarize.entity.SummaryQna;
import Alosius.PDF.Summarize.entity.SummaryRecord;
import Alosius.PDF.Summarize.entity.User;
import Alosius.PDF.Summarize.repository.SummaryRepository;
import Alosius.PDF.Summarize.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Persists and retrieves user PDF summaries, bookmarks, and Q&A pairs in PostgreSQL.
 */
@Service
public class SummaryPersistenceService {

    private final SummaryRepository summaryRepository;
    private final UserRepository userRepository;

    public SummaryPersistenceService(SummaryRepository summaryRepository, UserRepository userRepository) {
        this.summaryRepository = summaryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SummaryResponse saveSummary(Long userId, SummaryResponse response, Long fileSizeBytes, String summaryFormat) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        SummaryRecord record = new SummaryRecord();
        record.setUser(user);
        record.setFileName(response.getFileName());
        record.setFileSizeBytes(fileSizeBytes);
        record.setTotalPages(response.getTotalPages());
        record.setWordCount(response.getWordCount());
        record.setChunksProcessed(response.getChunksProcessed());
        record.setSummaryFormat(summaryFormat != null ? summaryFormat : "paragraph");
        record.setParagraphSummary(response.getParagraphSummary());
        record.setModelUsed(response.getModelUsed());
        record.setProcessingTimeMs(response.getProcessingTimeMs());
        record.setSaved(false);
        record.replaceBulletPoints(response.getBulletPoints());

        SummaryRecord saved = summaryRepository.save(record);
        response.setId(saved.getId());
        response.setSaved(saved.isSaved());
        response.setSummaryType(saved.getSummaryFormat());
        response.setCreatedAt(saved.getCreatedAt());
        return response;
    }

    @Transactional(readOnly = true)
    public List<SummaryResponse> getHistory(Long userId) {
        return summaryRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SummaryResponse> getSaved(Long userId) {
        return summaryRepository.findByUserIdAndSavedTrueOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SummaryResponse getOne(Long userId, Long summaryId) {
        SummaryRecord record = summaryRepository.findByIdAndUserId(summaryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Summary not found."));
        return toDtoWithQna(record);
    }

    @Transactional
    public boolean toggleSaved(Long userId, Long summaryId) {
        SummaryRecord record = summaryRepository.findByIdAndUserId(summaryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Summary not found."));
        record.setSaved(!record.isSaved());
        summaryRepository.save(record);
        return record.isSaved();
    }

    @Transactional
    public void deleteSummary(Long userId, Long summaryId) {
        SummaryRecord record = summaryRepository.findByIdAndUserId(summaryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Summary not found."));
        summaryRepository.delete(record);
    }

    @Transactional
    public void clearHistory(Long userId) {
        summaryRepository.deleteByUserId(userId);
    }

    @Transactional
    public void saveQnaText(Long userId, Long summaryId, String rawQa) {
        SummaryRecord record = summaryRepository.findByIdAndUserId(summaryId, userId).orElse(null);
        if (record == null || rawQa == null || rawQa.isBlank()) return;

        record.getQnaList().clear();
        for (String block : rawQa.split("\\n(?=Q:)")) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;
            int ansIdx = trimmed.indexOf("\nA:");
            if (ansIdx > 0) {
                SummaryQna qna = new SummaryQna();
                qna.setSummary(record);
                qna.setQuestion(trimmed.substring(0, ansIdx).replaceFirst("^Q:\\s*", "").trim());
                qna.setAnswer(trimmed.substring(ansIdx + 1).replaceFirst("^A:\\s*", "").trim());
                record.getQnaList().add(qna);
            }
        }
        summaryRepository.save(record);
    }

    private SummaryResponse toDto(SummaryRecord record) {
        List<String> bullets = record.getBulletPoints().stream()
                .map(SummaryBulletPoint::getBulletText)
                .toList();
        SummaryResponse dto = new SummaryResponse(
                record.getFileName(),
                record.getTotalPages(),
                record.getWordCount(),
                record.getChunksProcessed(),
                record.getParagraphSummary(),
                bullets,
                record.getModelUsed(),
                record.getProcessingTimeMs()
        );
        dto.setId(record.getId());
        dto.setSaved(record.isSaved());
        dto.setSummaryType(record.getSummaryFormat());
        dto.setCreatedAt(record.getCreatedAt());
        return dto;
    }

    private SummaryResponse toDtoWithQna(SummaryRecord record) {
        SummaryResponse dto = toDto(record);
        List<Map<String, String>> qnas = new ArrayList<>();
        for (SummaryQna qna : record.getQnaList()) {
            qnas.add(Map.of("question", qna.getQuestion(), "answer", qna.getAnswer()));
        }
        dto.setQnaList(qnas);
        return dto;
    }
}
