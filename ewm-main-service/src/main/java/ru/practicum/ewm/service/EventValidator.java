package ru.practicum.ewm.service;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.error.ConflictException;
import ru.practicum.ewm.error.ValidationException;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.model.enums.EventState;

import java.time.LocalDateTime;

@Component
public class EventValidator {

    public void validateUserEventDate(LocalDateTime eventDate) {
        if (eventDate != null && eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }
    }

    public void validateAdminEventDate(LocalDateTime eventDate) {
        if (eventDate != null && eventDate.isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ValidationException("Event date must be at least 1 hour from now");
        }
    }

    public void validatePublishEventDate(Event event, LocalDateTime newEventDate) {
        LocalDateTime dateToCheck = newEventDate != null ? newEventDate : event.getEventDate();
        if (dateToCheck.isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ValidationException("Event date must be at least 1 hour from publication date");
        }
    }

    public void validateUserCanUpdate(Event event) {
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }
    }

    public void validateEventForPublish(Event event) {
        if (event.getState() != EventState.PENDING) {
            throw new ConflictException(
                    "Cannot publish the event because it's not in the right state: " + event.getState());
        }
    }

    public void validateEventForReject(Event event) {
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Cannot reject a published event");
        }
    }

    public void validateDateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("rangeStart must not be after rangeEnd");
        }
    }
}
