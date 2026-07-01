package ru.practicum.stats.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.stats.dto.EndpointHitDto;
import ru.practicum.stats.dto.ViewStatsDto;
import ru.practicum.stats.model.EndpointHit;
import ru.practicum.stats.repository.HitRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    private final HitRepository hitRepository;

    @Override
    @Transactional
    public void saveHit(EndpointHitDto dto) {
        EndpointHit hit = EndpointHit.builder()
                .app(dto.getApp())
                .uri(dto.getUri())
                .ip(dto.getIp())
                .timestamp(dto.getTimestamp())
                .build();
        hitRepository.save(hit);
    }

    @Override
    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, boolean unique) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must not be after end date");
        }
        if (uris == null || uris.isEmpty()) {
            return unique
                    ? hitRepository.findStatsUnique(start, end)
                    : hitRepository.findStats(start, end);
        }
        return unique
                ? hitRepository.findStatsByUrisUnique(start, end, uris)
                : hitRepository.findStatsByUris(start, end, uris);
    }
}
