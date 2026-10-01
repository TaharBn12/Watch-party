package com.watchparty.common.validation;

import com.watchparty.common.exception.BadRequestException;
import com.watchparty.common.model.VideoType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoSourceServiceTest {

    private final VideoSourceService videoSourceService = new VideoSourceService();

    @Test
    void detectsYouTubeUrls() {
        String normalized = videoSourceService.normalizeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertEquals(VideoType.YOUTUBE, videoSourceService.detectVideoType(normalized));
    }

    @Test
    void detectsMp4Urls() {
        String normalized = videoSourceService.normalizeUrl("https://cdn.example.com/video.mp4");
        assertEquals(VideoType.MP4, videoSourceService.detectVideoType(normalized));
    }

    @Test
    void rejectsUnsupportedUrls() {
        String normalized = videoSourceService.normalizeUrl("https://example.com/video.m3u8");
        assertThrows(BadRequestException.class, () -> videoSourceService.detectVideoType(normalized));
    }

    @Test
    void rejectsUnsafeSchemes() {
        assertThrows(BadRequestException.class, () -> videoSourceService.normalizeUrl("javascript:alert(1)"));
    }
}
