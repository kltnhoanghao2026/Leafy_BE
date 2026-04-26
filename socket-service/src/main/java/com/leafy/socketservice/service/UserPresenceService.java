package com.leafy.socketservice.service;

import com.leafy.socketservice.model.ChatUser;

import java.util.List;

public interface UserPresenceService {
    ChatUser saveUser(ChatUser user);
    void disconnect(String userId);
    List<ChatUser> findConnectedUsers();
}
