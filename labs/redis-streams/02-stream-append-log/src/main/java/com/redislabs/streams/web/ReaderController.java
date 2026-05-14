package com.redislabs.streams.web;

import com.redislabs.streams.stream.ReaderCursorService;
import com.redislabs.streams.stream.ReaderEvent;
import com.redislabs.streams.stream.RecentReaderEvents;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reader")
public class ReaderController {

    private final ReaderCursorService cursorService;
    private final RecentReaderEvents recentReaderEvents;

    public ReaderController(ReaderCursorService cursorService, RecentReaderEvents recentReaderEvents) {
        this.cursorService = cursorService;
        this.recentReaderEvents = recentReaderEvents;
    }

    @GetMapping("/cursor")
    public Map<String, Object> cursor() {
        return Map.of("cursor", cursorService.currentCursor());
    }

    @PostMapping("/cursor")
    public Map<String, Object> setCursor(@RequestParam(defaultValue = "0-0") String value) {
        return cursorService.setCursor(value);
    }

    @PostMapping("/cursor/reset")
    public Map<String, Object> resetCursor() {
        return cursorService.resetCursor();
    }

    @GetMapping("/messages")
    public List<ReaderEvent> messages() {
        return recentReaderEvents.snapshot();
    }
}
