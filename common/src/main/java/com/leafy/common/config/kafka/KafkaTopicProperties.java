package com.leafy.common.config.kafka;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kafka.topics")
@Getter
@Setter
public class KafkaTopicProperties {
    
    private UserEvents userEvents = new UserEvents();
    private MessageEvents messageEvents = new MessageEvents();
    private NotificationEvents notificationEvents = new NotificationEvents();
    private CommunityEvents communityEvents = new CommunityEvents();

    @Getter
    @Setter
    public static class UserEvents {
        private String registered = "user.registered";
        private String updated = "user.updated";
        private String deleted = "user.deleted";
        private String verified = "user.verified";
        private String enabled = "user.enabled";
        private String disabled = "user.disabled";
    }
    
    @Getter
    @Setter
    public static class MessageEvents {

    }

    @Getter
    @Setter
    public static class NotificationEvents {

    }

    @Getter
    @Setter
    public static class SystemEvents {

    }

    @Getter
    @Setter
    public static class CommunityEvents {
        private String commentCreated = "community.comment.created";
        private String commentDeleted = "community.comment.deleted";
        private String voteCreated = "community.vote.created";
        private String voteDeleted = "community.vote.deleted";
    }
}
