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
    private ProfileEvents profileEvents = new ProfileEvents();
    private SocketEvents socketEvents = new SocketEvents();
    private SystemEvents systemEvents = new SystemEvents();

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
    public static class SocketEvents {
        private String socketEvents = "socket.events";
    }

    @Getter
    @Setter
    public static class NotificationEvents {

    }

    @Getter
    @Setter
    public static class SystemEvents {
        private String planApplied = "system.plan.applied";
    }

    @Getter
    @Setter
    public static class CommunityEvents {
        private String postUpserted = "community.post.upserted";
        private String postDeleted = "community.post.deleted";
        private String commentCreated = "community.comment.created";
        private String commentDeleted = "community.comment.deleted";
        private String voteCreated = "community.vote.created";
        private String voteDeleted = "community.vote.deleted";
    }

    @Getter
    @Setter
    public static class ProfileEvents {
        private String created = "profile.created";
        private String updated = "profile.updated";
        private String deleted = "profile.deleted";
        private String connectionUpdated = "profile.connection.updated";
    }
}
