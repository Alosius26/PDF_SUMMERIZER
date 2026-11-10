package Alosius.PDF.Summarize.repository;

import Alosius.PDF.Summarize.entity.SummaryRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SummaryRepository extends JpaRepository<SummaryRecord, Long> {

    @EntityGraph(attributePaths = {"bulletPoints"})
    List<SummaryRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"bulletPoints"})
    List<SummaryRecord> findByUserIdAndSavedTrueOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"bulletPoints"})
    Optional<SummaryRecord> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"bulletPoints"})
    Optional<SummaryRecord> findByShareToken(String shareToken);

    void deleteByUserId(Long userId);
}
