package com.leafy.notificationservice.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(FirebaseMessaging.class)
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
public class FirebasePushService implements PushDeliveryService {

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public String sendToToken(String token, String title, String body, Map<String, String> data)
            throws PushDeliveryException {

        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .build();

        try {
            return firebaseMessaging.send(message);
        } catch (FirebaseMessagingException exception) {
            String errorCode = exception.getMessagingErrorCode() != null
                    ? exception.getMessagingErrorCode().name()
                    : String.valueOf(exception.getErrorCode());
            throw new PushDeliveryException(errorCode, exception.getMessage(), exception);
        }
    }
}
