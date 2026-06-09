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
public class CommentUpdateRequest {

    @NotBlank(message = "Content cannot be blank")
    String content;

    List<PostMedia> media;
}
