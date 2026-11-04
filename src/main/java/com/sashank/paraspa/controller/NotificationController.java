package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.NotificationResponse;
import com.sashank.paraspa.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public ResponseEntity<CursorPageResponse<NotificationResponse>> getNotifications(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getNotifications(userId, cursor, size));
    }

    /** Bare number, like GET /api/messages/unread-count. */
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    // read-all is declared first and has a fixed path; it cannot clash with /{id}/read
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@RequestAttribute("userId") Long userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {
        notificationService.markRead(userId, id);
        return ResponseEntity.noContent().build();
    }
}
