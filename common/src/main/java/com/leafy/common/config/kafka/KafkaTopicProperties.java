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
    private ProfileEvents profileEvents = new ProfileEvents();
    private PostEvents postEvents = new PostEvents();

    @Getter
    @Setter
    public static class ProfileEvents {
        private String created = "profile.created";
    }

    @Getter
    @Setter
    public static class PostEvents {
        private String upserted = "post.upserted";
        private String deleted = "post.deleted";
    }

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


}
