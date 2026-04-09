package com.leafy.searchservice.model.elasticsearch;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(indexName = "posts")
@Setting(shards = 3, replicas = 1)
public class PostIndex {

    @Id
    String id;

    @Field(type = FieldType.Keyword)
    String authorId;

        @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "standard"),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
        )
    String authorName;

        @Field(type = FieldType.Keyword, index = false)
    String authorAvatar;

    @Field(type = FieldType.Keyword)
    String authorRole;

    @Field(type = FieldType.Boolean)
    Boolean authorVerified;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "english"),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
    )
    String title;

    @Field(type = FieldType.Text, analyzer = "english")
    String caption;

    @Field(type = FieldType.Keyword)
    List<String> hashtags;

    @Field(type = FieldType.Keyword)
    String postType;

    @Field(type = FieldType.Integer)
    Integer upvoteCount;

    @Field(type = FieldType.Integer)
    Integer commentCount;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    LocalDateTime uploadedAt;
}