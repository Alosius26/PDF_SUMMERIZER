package Alosius.PDF.Summarize.repository;

import Alosius.PDF.Summarize.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}
