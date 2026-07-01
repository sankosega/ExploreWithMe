package ru.practicum.ewm.service;

import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.NewCommentDto;
import ru.practicum.ewm.dto.UpdateCommentDto;

import java.util.List;

public interface CommentService {

    CommentDto create(Long userId, Long eventId, NewCommentDto dto);

    CommentDto update(Long userId, Long commentId, UpdateCommentDto dto);

    void delete(Long userId, Long commentId);

    List<CommentDto> getUserComments(Long userId, int from, int size);

    List<CommentDto> getEventComments(Long eventId, int from, int size);

    void adminDelete(Long commentId);

    List<CommentDto> adminSearch(Long eventId, Long authorId, int from, int size);
}
