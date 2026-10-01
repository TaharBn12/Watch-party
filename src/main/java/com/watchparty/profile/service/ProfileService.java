package com.watchparty.profile.service;

import com.watchparty.common.exception.ResourceNotFoundException;
import com.watchparty.common.validation.InputSanitizer;
import com.watchparty.profile.entity.ProfileEntity;
import com.watchparty.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final InputSanitizer inputSanitizer;

    public ProfileService(ProfileRepository profileRepository, InputSanitizer inputSanitizer) {
        this.profileRepository = profileRepository;
        this.inputSanitizer = inputSanitizer;
    }

    @Transactional(readOnly = true)
    public ProfileEntity requireProfile(UUID profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("الملف الشخصي غير موجود"));
    }

    @Transactional
    public ProfileEntity updateProfile(UUID profileId, String displayName, String avatarUrl) {
        ProfileEntity profile = requireProfile(profileId);
        profile.setDisplayName(inputSanitizer.sanitizeRequiredText(displayName, "الاسم الظاهر", 1, 80));

        if (StringUtils.hasText(avatarUrl)) {
            profile.setAvatarUrl(inputSanitizer.sanitizeOptionalText(avatarUrl, "رابط الصورة", 2048));
        } else {
            profile.setAvatarUrl(null);
        }

        return profileRepository.save(profile);
    }
}
