package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.NewCommentDto;
import ru.practicum.ewm.dto.UpdateCommentDto;
import ru.practicum.ewm.error.ConflictException;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.mapper.CommentMapper;
import ru.practicum.ewm.model.Comment;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.model.User;
import ru.practicum.ewm.model.enums.EventState;
import ru.practicum.ewm.repository.CommentRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CommentDto create(Long userId, Long eventId, NewCommentDto dto) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Can only comment on published events");
        }
        Comment comment = Comment.builder()
                .text(dto.getText())
                .author(author)
                .event(event)
                .createdOn(LocalDateTime.now())
                .build();
        return CommentMapper.toDto(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public CommentDto update(Long userId, Long commentId, UpdateCommentDto dto) {
        Comment comment = commentRepository.findByIdAndAuthorId(commentId, userId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));
        comment.setText(dto.getText());
        comment.setLastUpdatedOn(LocalDateTime.now());
        return CommentMapper.toDto(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long commentId) {
        Comment comment = commentRepository.findByIdAndAuthorId(commentId, userId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));
        commentRepository.delete(comment);
    }

    @Override
    public List<CommentDto> getUserComments(Long userId, int from, int size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }
        PageRequest page = PageRequest.of(from / size, size, Sort.by(Sort.Direction.DESC, "createdOn"));
        return commentRepository.findAllByAuthorId(userId, page).stream()
                .map(CommentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<CommentDto> getEventComments(Long eventId, int from, int size) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        PageRequest page = PageRequest.of(from / size, size, Sort.by(Sort.Direction.DESC, "createdOn"));
        return commentRepository.findAllByEventId(eventId, page).stream()
                .map(CommentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void adminDelete(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }
        commentRepository.deleteById(commentId);
    }

    @Override
    public List<CommentDto> adminSearch(Long eventId, Long authorId, int from, int size) {
        PageRequest page = PageRequest.of(from / size, size, Sort.by(Sort.Direction.DESC, "createdOn"));
        Specification<Comment> spec = Specification.where(null);
        if (eventId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("event").get("id"), eventId));
        }
        if (authorId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("author").get("id"), authorId));
        }
        return commentRepository.findAll(spec, page).getContent().stream()
                .map(CommentMapper::toDto)
                .collect(Collectors.toList());
    }
}
