package com.leafy.communityfeedservice.dto.request;

import com.leafy.communityfeedservice.model.embedded.PostMedia;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentCreateRequest {

    @NotBlank(message = "Post ID cannot be blank")
    String postId;

    String parentId; // Optional, for replies

    @NotBlank(message = "Content cannot be blank")
    String content;

    List<PostMedia> media;
}
