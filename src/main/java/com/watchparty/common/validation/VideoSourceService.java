package com.watchparty.common.validation;

import com.watchparty.common.exception.BadRequestException;
import com.watchparty.common.model.VideoType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Service
public class VideoSourceService {

    public String normalizeUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new BadRequestException("رابط الفيديو مطلوب");
        }

        String trimmed = rawUrl.trim();
        if (trimmed.length() > 2048) {
            throw new BadRequestException("رابط الفيديو أطول من الحد المسموح");
        }
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new BadRequestException("رابط الفيديو يحتوي محارف غير صالحة");
        }

        URI uri = parseUri(trimmed);
        String scheme = lower(uri.getScheme());
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new BadRequestException("يجب أن يبدأ رابط الفيديو بـ http أو https");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new BadRequestException("رابط الفيديو لا يحتوي اسم نطاق صالح");
        }

        return uri.normalize().toString();
    }

    public VideoType detectVideoType(String normalizedUrl) {
        URI uri = parseUri(normalizedUrl);
        String host = lower(uri.getHost());
        String path = lower(uri.getPath());

        if (isYouTubeHost(host)) {
            return VideoType.YOUTUBE;
        }
        if (path.endsWith(".mp4")) {
            return VideoType.MP4;
        }

        throw new BadRequestException("مصدر الفيديو غير مدعوم. استخدم رابط YouTube أو رابط MP4 مباشر");
    }

    public VideoType detectAndValidate(String rawUrl) {
        return detectVideoType(normalizeUrl(rawUrl));
    }

    private URI parseUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new BadRequestException("رابط الفيديو غير صالح");
        }
    }

    private boolean isYouTubeHost(String host) {
        return host.equals("youtube.com")
                || host.equals("www.youtube.com")
                || host.equals("m.youtube.com")
                || host.equals("youtu.be")
                || host.equals("www.youtu.be")
                || host.equals("youtube-nocookie.com")
                || host.equals("www.youtube-nocookie.com");
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
