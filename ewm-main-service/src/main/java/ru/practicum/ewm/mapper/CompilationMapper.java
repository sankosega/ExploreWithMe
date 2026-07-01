package ru.practicum.ewm.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.ewm.dto.CompilationDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.UpdateCompilationRequest;
import ru.practicum.ewm.model.Compilation;
import ru.practicum.ewm.model.Event;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class CompilationMapper {

    public void update(Compilation compilation, UpdateCompilationRequest dto, Set<Event> events) {
        if (dto.getTitle() != null) {
            compilation.setTitle(dto.getTitle());
        }
        if (dto.getPinned() != null) {
            compilation.setPinned(dto.getPinned());
        }
        if (events != null) {
            compilation.setEvents(events);
        }
    }

    public CompilationDto toDto(Compilation compilation,
                                Map<Long, Long> confirmedRequestsMap,
                                Map<Long, Long> viewsMap) {
        Set<EventShortDto> eventDtos = compilation.getEvents().stream()
                .map(e -> EventMapper.toShortDto(
                        e,
                        confirmedRequestsMap.getOrDefault(e.getId(), 0L),
                        viewsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toSet());
        return CompilationDto.builder()
                .id(compilation.getId())
                .pinned(compilation.isPinned())
                .title(compilation.getTitle())
                .events(eventDtos)
                .build();
    }
}
