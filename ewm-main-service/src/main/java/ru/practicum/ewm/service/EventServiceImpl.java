package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.EventFullDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.NewEventDto;
import ru.practicum.ewm.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.dto.UpdateEventUserRequest;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.Category;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.model.Location;
import ru.practicum.ewm.model.User;
import ru.practicum.ewm.model.enums.AdminStateAction;
import ru.practicum.ewm.model.enums.EventSort;
import ru.practicum.ewm.model.enums.EventState;
import ru.practicum.ewm.model.enums.UserStateAction;
import ru.practicum.ewm.repository.CategoryRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventValidator eventValidator;
    private final StatsFacade statsFacade;
    private final RequestCounterService requestCounterService;

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException("Category with id=" + dto.getCategory()
                        + " was not found"));
        eventValidator.validateUserEventDate(dto.getEventDate());
        Event event = eventRepository.save(EventMapper.toEntity(dto, category, user));
        return EventMapper.toFullDto(event, 0L, 0L);
    }

    @Override
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
        PageRequest page = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiator(user, page);
        return enrichShort(events);
    }

    @Override
    public EventFullDto getUserEvent(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        return enrichFull(event);
    }

    @Override
    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest dto) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        eventValidator.validateUserCanUpdate(event);
        eventValidator.validateUserEventDate(dto.getEventDate());
        applyUserUpdate(event, dto);
        return enrichFull(eventRepository.save(event));
    }

    @Override
    public List<EventFullDto> adminSearchEvents(List<Long> users, List<EventState> states,
                                                 List<Long> categories, LocalDateTime rangeStart,
                                                 LocalDateTime rangeEnd, int from, int size) {
        Specification<Event> spec = buildAdminSpec(users, states, categories, rangeStart, rangeEnd);
        PageRequest page = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAll(spec, page).getContent();
        return events.stream().map(this::enrichFull).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto adminUpdateEvent(Long eventId, UpdateEventAdminRequest dto) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        eventValidator.validateAdminEventDate(dto.getEventDate());
        if (dto.getStateAction() == AdminStateAction.PUBLISH_EVENT) {
            eventValidator.validateEventForPublish(event);
            eventValidator.validatePublishEventDate(event, dto.getEventDate());
            event.setPublishedOn(LocalDateTime.now());
            event.setState(EventState.PUBLISHED);
        } else if (dto.getStateAction() == AdminStateAction.REJECT_EVENT) {
            eventValidator.validateEventForReject(event);
            event.setState(EventState.CANCELED);
        }
        applyAdminUpdate(event, dto);
        return enrichFull(eventRepository.save(event));
    }

    @Override
    public List<EventShortDto> publicSearchEvents(String text, List<Long> categories, Boolean paid,
                                                   LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                   boolean onlyAvailable, EventSort sort,
                                                   int from, int size, String ip) {
        eventValidator.validateDateRange(rangeStart, rangeEnd);
        statsFacade.recordHit("/events", ip);

        Sort dbSort = (sort == EventSort.VIEWS)
                ? Sort.unsorted()
                : Sort.by(Sort.Direction.ASC, "eventDate");
        PageRequest page = PageRequest.of(from / size, size, dbSort);

        Specification<Event> spec = buildPublicSpec(text, categories, paid, rangeStart, rangeEnd);
        List<Event> events = eventRepository.findAll(spec, page).getContent();

        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        Map<Long, Long> confirmedMap = requestCounterService.getConfirmedRequestsMap(eventIds);
        Map<Long, Long> viewsMap = statsFacade.getViewsMap(eventIds);

        if (onlyAvailable) {
            events = events.stream()
                    .filter(e -> e.getParticipantLimit() == 0
                            || confirmedMap.getOrDefault(e.getId(), 0L) < e.getParticipantLimit())
                    .collect(Collectors.toList());
        }

        List<EventShortDto> result = events.stream()
                .map(e -> EventMapper.toShortDto(e,
                        confirmedMap.getOrDefault(e.getId(), 0L),
                        viewsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());

        if (sort == EventSort.VIEWS) {
            result.sort(Comparator.comparingLong((EventShortDto e) -> e.getViews() == null ? 0 : e.getViews())
                    .reversed());
        }

        return result;
    }

    @Override
    public EventFullDto publicGetEvent(Long id, String ip) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event with id=" + id + " was not found"));
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + id + " was not found");
        }
        statsFacade.recordHit("/events/" + id, ip);
        return enrichFull(event);
    }

    private EventFullDto enrichFull(Event event) {
        long confirmed = requestCounterService.countConfirmed(event.getId());
        long views = statsFacade.getViewsMap(List.of(event.getId())).getOrDefault(event.getId(), 0L);
        return EventMapper.toFullDto(event, confirmed, views);
    }

    private List<EventShortDto> enrichShort(List<Event> events) {
        List<Long> ids = events.stream().map(Event::getId).collect(Collectors.toList());
        Map<Long, Long> confirmedMap = requestCounterService.getConfirmedRequestsMap(ids);
        Map<Long, Long> viewsMap = statsFacade.getViewsMap(ids);
        return events.stream()
                .map(e -> EventMapper.toShortDto(e,
                        confirmedMap.getOrDefault(e.getId(), 0L),
                        viewsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private void applyUserUpdate(Event event, UpdateEventUserRequest dto) {
        if (dto.getAnnotation() != null) event.setAnnotation(dto.getAnnotation());
        if (dto.getDescription() != null) event.setDescription(dto.getDescription());
        if (dto.getTitle() != null) event.setTitle(dto.getTitle());
        if (dto.getEventDate() != null) event.setEventDate(dto.getEventDate());
        if (dto.getPaid() != null) event.setPaid(dto.getPaid());
        if (dto.getParticipantLimit() != null) event.setParticipantLimit(dto.getParticipantLimit());
        if (dto.getRequestModeration() != null) event.setRequestModeration(dto.getRequestModeration());
        if (dto.getCategory() != null) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category with id=" + dto.getCategory()
                            + " was not found"));
            event.setCategory(category);
        }
        if (dto.getLocation() != null) {
            event.setLocation(new Location(dto.getLocation().getLat(), dto.getLocation().getLon()));
        }
        if (dto.getStateAction() == UserStateAction.SEND_TO_REVIEW) {
            event.setState(EventState.PENDING);
        } else if (dto.getStateAction() == UserStateAction.CANCEL_REVIEW) {
            event.setState(EventState.CANCELED);
        }
    }

    private void applyAdminUpdate(Event event, UpdateEventAdminRequest dto) {
        if (dto.getAnnotation() != null) event.setAnnotation(dto.getAnnotation());
        if (dto.getDescription() != null) event.setDescription(dto.getDescription());
        if (dto.getTitle() != null) event.setTitle(dto.getTitle());
        if (dto.getEventDate() != null) event.setEventDate(dto.getEventDate());
        if (dto.getPaid() != null) event.setPaid(dto.getPaid());
        if (dto.getParticipantLimit() != null) event.setParticipantLimit(dto.getParticipantLimit());
        if (dto.getRequestModeration() != null) event.setRequestModeration(dto.getRequestModeration());
        if (dto.getCategory() != null) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category with id=" + dto.getCategory()
                            + " was not found"));
            event.setCategory(category);
        }
        if (dto.getLocation() != null) {
            event.setLocation(new Location(dto.getLocation().getLat(), dto.getLocation().getLon()));
        }
    }

    private Specification<Event> buildAdminSpec(List<Long> users, List<EventState> states,
                                                 List<Long> categories, LocalDateTime rangeStart,
                                                 LocalDateTime rangeEnd) {
        Specification<Event> spec = Specification.where(null);
        if (users != null && !users.isEmpty()) {
            spec = spec.and((root, q, cb) -> root.get("initiator").get("id").in(users));
        }
        if (states != null && !states.isEmpty()) {
            spec = spec.and((root, q, cb) -> root.get("state").in(states));
        }
        if (categories != null && !categories.isEmpty()) {
            spec = spec.and((root, q, cb) -> root.get("category").get("id").in(categories));
        }
        if (rangeStart != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("eventDate"), rangeStart));
        }
        if (rangeEnd != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
        }
        return spec;
    }

    private Specification<Event> buildPublicSpec(String text, List<Long> categories, Boolean paid,
                                                  LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        Specification<Event> spec = (root, q, cb) -> cb.equal(root.get("state"), EventState.PUBLISHED);
        if (text != null && !text.isBlank()) {
            String pattern = "%" + text.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("annotation")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            ));
        }
        if (categories != null && !categories.isEmpty()) {
            spec = spec.and((root, q, cb) -> root.get("category").get("id").in(categories));
        }
        if (paid != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("paid"), paid));
        }
        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        spec = spec.and((root, q, cb) -> cb.greaterThan(root.get("eventDate"), start));
        if (rangeEnd != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
        }
        return spec;
    }
}
