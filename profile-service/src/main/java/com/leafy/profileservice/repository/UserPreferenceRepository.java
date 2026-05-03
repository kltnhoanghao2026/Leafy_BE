package com.leafy.profileservice.repository;

import com.leafy.profileservice.model.UserPreference;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Custom repository for UserPreference with MongoDB dot notation updates
 * This approach avoids loading entire Profile documents into memory
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class UserPreferenceRepository {

    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION_NAME = "profile";

    /**
     * Get user preference by user ID
     */
    public UserPreference getUserPreferenceByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        query.fields().include("userPreference");
        
        var profile = mongoTemplate.findOne(query, org.bson.Document.class, COLLECTION_NAME);
        if (profile == null || !profile.containsKey("userPreference")) {
            log.debug("No preferences found for userId: {}, returning defaults", userId);
            return new UserPreference();
        }
        
        return mongoTemplate.getConverter().read(UserPreference.class, 
                (org.bson.Document) profile.get("userPreference"));
    }

    /**
     * Get nested setting by field path
     */
    public <T> T getNestedSetting(String userId, String fieldPath, Class<T> settingClass) {
        Query query = new Query(Criteria.where("userId").is(userId));
        query.fields().include("userPreference." + fieldPath);
        
        var profile = mongoTemplate.findOne(query, org.bson.Document.class, COLLECTION_NAME);
        if (profile == null) {
            log.warn("Profile not found for userId: {}", userId);
            try {
                return settingClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed to create default instance", e);
                return null;
            }
        }
        
        org.bson.Document userPreference = (org.bson.Document) profile.get("userPreference");
        if (userPreference == null || !userPreference.containsKey(fieldPath)) {
            try {
                return settingClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed to create default instance", e);
                return null;
            }
        }
        
        return mongoTemplate.getConverter().read(settingClass, 
                (org.bson.Document) userPreference.get(fieldPath));
    }

    /**
     * Update entire user preference
     */
    public boolean updateUserPreference(String userId, UserPreference userPreference) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update().set("userPreference", userPreference);
        
        UpdateResult result = mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
        return result.getModifiedCount() > 0;
    }

    /**
     * Update general settings using dot notation
     */
    public boolean updateGeneralSettings(String userId, UserPreference.GeneralSettings settings) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update()
                .set("userPreference.generalSettings.showAllFriends", settings.isShowAllFriends())
                .set("userPreference.generalSettings.languageEn", settings.isLanguageEn());
        
        UpdateResult result = mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
        return result.getModifiedCount() > 0;
    }

    /**
     * Update security settings using dot notation
     */
    public boolean updateSecuritySettings(String userId, UserPreference.SecuritySettings settings) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update()
                .set("userPreference.securitySettings.twoFactorEnabled", settings.isTwoFactorEnabled());
        
        UpdateResult result = mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
        return result.getModifiedCount() > 0;
    }

    /**
     * Update privacy settings using dot notation
     */
    public boolean updatePrivacySettings(String userId, UserPreference.PrivacySettings settings) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update()
                .set("userPreference.privacySettings.showDob", settings.getShowDob())
                .set("userPreference.privacySettings.showActiveStatus", settings.isShowActiveStatus())
                .set("userPreference.privacySettings.showReadStatus", settings.isShowReadStatus())
                .set("userPreference.privacySettings.canText", settings.getCanText())
                .set("userPreference.privacySettings.canCall", settings.getCanCall())
                .set("userPreference.privacySettings.showPosts", settings.isShowPosts())
                .set("userPreference.privacySettings.showPostAfter", settings.getShowPostAfter())
                .set("userPreference.privacySettings.allowSearchOnPhoneNumber", settings.isAllowSearchOnPhoneNumber());
        
        UpdateResult result = mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
        return result.getModifiedCount() > 0;
    }

    /**
     * Update sync settings using dot notation
     */
    public boolean updateSyncSettings(String userId, UserPreference.SyncSettings settings) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update()
                .set("userPreference.syncSettings.syncSuggestion", settings.isSyncSuggestion())
                .set("userPreference.syncSettings.showSyncProgress", settings.isShowSyncProgress());
        
        UpdateResult result = mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
        return result.getModifiedCount() > 0;
    }

    /**
     * Update appearance settings using dot notation
     */
    public boolean updateAppearanceSettings(String userId, UserPreference.AppearanceSettings settings) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update()
                .set("userPreference.appearanceSettings.theme", settings.isTheme());
        
        UpdateResult result = mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
        return result.getModifiedCount() > 0;
    }

    /**
     * Update message settings using dot notation
     */
    public boolean updateMessageSettings(String userId, UserPreference.MessageSettings settings) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update()
                .set("userPreference.messageSettings.quickResponseEnable", settings.isQuickResponseEnable());
        
        UpdateResult result = mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
        return result.getModifiedCount() > 0;
    }

    /**
     * Update notification settings using dot notation
     */
    public boolean updateNotificationSettings(String userId, UserPreference.NotificationSettings settings) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update()
                .set("userPreference.notificationSettings.notifyNewMessageFromDirect", settings.isNotifyNewMessageFromDirect())
                .set("userPreference.notificationSettings.previewNewMessageFromDirect", settings.isPreviewNewMessageFromDirect())
                .set("userPreference.notificationSettings.notifyNewMessageFromGroup", settings.isNotifyNewMessageFromGroup())
                .set("userPreference.notificationSettings.notifyCall", settings.isNotifyCall())
                .set("userPreference.notificationSettings.notifyNewPostFromFriend", settings.isNotifyNewPostFromFriend())
                .set("userPreference.notificationSettings.notifyDOB", settings.isNotifyDOB())
                .set("userPreference.notificationSettings.notifyNewMessage", settings.isNotifyNewMessage())
                .set("userPreference.notificationSettings.shakeOnNewMessage", settings.isShakeOnNewMessage())
                .set("userPreference.notificationSettings.previewNewMessage", settings.isPreviewNewMessage());
        
        UpdateResult result = mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
        return result.getModifiedCount() > 0;
    }

    /**
     * Update utilities settings using dot notation
     */
    public boolean updateUtilitiesSettings(String userId, UserPreference.UtilitiesSettings settings) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update()
                .set("userPreference.utilitiesSettings.stickerSuggestion", settings.isStickerSuggestion());
        
        UpdateResult result = mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
        return result.getModifiedCount() > 0;
    }
}
