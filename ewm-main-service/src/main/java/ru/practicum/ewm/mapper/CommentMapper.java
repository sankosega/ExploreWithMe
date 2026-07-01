package ru.practicum.ewm.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.model.Comment;

@UtilityClass
public class CommentMapper {

    public CommentDto toDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .eventId(comment.getEvent().getId())
                .createdOn(comment.getCreatedOn())
                .lastUpdatedOn(comment.getLastUpdatedOn())
                .build();
    }
}
