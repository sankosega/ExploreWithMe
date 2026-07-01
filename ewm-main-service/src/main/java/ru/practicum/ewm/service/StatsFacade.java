package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class StatsFacade {

    private final StatsClient statsClient;

    public Map<Long, Long> getViewsMap(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<String> uris = eventIds.stream()
                    .map(id -> "/events/" + id)
                    .collect(Collectors.toList());
            LocalDateTime start = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
            List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), uris, true);
            return stats.stream()
                    .collect(Collectors.toMap(
                            s -> Long.parseLong(s.getUri().replace("/events/", "")),
                            ViewStatsDto::getHits
                    ));
        } catch (Exception e) {
            log.warn("Failed to fetch stats: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    public void recordHit(String uri, String ip) {
        try {
            statsClient.recordHit("ewm-main-service", uri, ip, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to record hit: {}", e.getMessage());
        }
    }
}
