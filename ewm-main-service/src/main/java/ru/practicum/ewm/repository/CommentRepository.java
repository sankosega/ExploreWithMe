package ru.practicum.ewm.repository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.practicum.ewm.model.Comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long>, JpaSpecificationExecutor<Comment> {

    List<Comment> findAllByEventId(Long eventId, PageRequest pageable);

    List<Comment> findAllByAuthorId(Long authorId, PageRequest pageable);

    Optional<Comment> findByIdAndAuthorId(Long id, Long authorId);
}
