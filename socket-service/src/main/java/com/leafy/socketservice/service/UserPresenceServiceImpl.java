package com.leafy.socketservice.service;

import com.leafy.common.enums.Status;
import com.leafy.socketservice.dto.PresenceEvent;
import com.leafy.socketservice.model.ChatUser;
import com.leafy.socketservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPresenceServiceImpl implements UserPresenceService {

    private final ChatUserRepository repository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public ChatUser saveUser(ChatUser user) {
        ChatUser savedUser = repository.findById(user.getId())
                .map(stored -> {
                    stored.setStatus(Status.ONLINE);
                    if (user.getFullName() != null && !user.getFullName().isBlank()) {
                        stored.setFullName(user.getFullName());
                    }
                    if (user.getEmail() != null && !user.getEmail().isBlank()) {
                        stored.setEmail(user.getEmail());
                    }
                    if (user.getAvatar() != null && !user.getAvatar().isBlank()) {
                        stored.setAvatar(user.getAvatar());
                    }
                    if (user.getProfileId() != null && !user.getProfileId().isBlank()) {
                        stored.setProfileId(user.getProfileId());
                    }
                    stored.setLastUpdatedAt(LocalDateTime.now());
                    log.info("[Presence] User ONLINE: {}", stored.getEmail());
                    return repository.save(stored);
                })
                .orElseGet(() -> {
                    user.setStatus(Status.ONLINE);
                    user.setLastUpdatedAt(LocalDateTime.now());
                    user.setFullName(user.getFullName() != null && !user.getFullName().isBlank()
                            ? user.getFullName()
                            : "Người dùng");
                    if (user.getFriendIds() == null) {
                        user.setFriendIds(new HashSet<>());
                    }
                    log.info("[Presence] New user ONLINE: {}", user.getEmail());
                    return repository.save(user);
                });

        notifyFriendsAboutPresence(savedUser, Status.ONLINE);
        return savedUser;
    }

    @Override
    public void disconnect(String userId) {
        repository.findById(userId).ifPresent(user -> {
            user.setStatus(Status.OFFLINE);
            repository.save(user);
            log.info("[Presence] User OFFLINE: {}", user.getEmail());
            notifyFriendsAboutPresence(user, Status.OFFLINE);
        });
    }

    private void notifyFriendsAboutPresence(ChatUser user, Status status) {
        if (user.isInvisible() || user.getFriendIds() == null || user.getFriendIds().isEmpty()) return;

        List<ChatUser> onlineFriends = repository.findByIdInAndStatus(user.getFriendIds(), Status.ONLINE);
        PresenceEvent event = new PresenceEvent(user.getId(), status);

        for (ChatUser friend : onlineFriends) {
            messagingTemplate.convertAndSendToUser(friend.getId(), "/queue/presence", event);
        }

        // When coming online: also push back the list of already-online friends to the user
        if (status == Status.ONLINE) {
            for (ChatUser friend : onlineFriends) {
                messagingTemplate.convertAndSendToUser(
                        user.getId(), "/queue/presence", new PresenceEvent(friend.getId(), Status.ONLINE));
            }
        }
    }

    @Override
    public List<ChatUser> findConnectedUsers() {
        return repository.findAllByStatus(Status.ONLINE);
    }
}
