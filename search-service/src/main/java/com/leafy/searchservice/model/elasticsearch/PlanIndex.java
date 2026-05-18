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
@Document(indexName = "plans")
@Setting(settingPath = "elasticsearch/es-setting.json")
public class PlanIndex {

    @Id
    String id;

    // ── Source tracking ───────────────────────────────────────────────────────

    @Field(type = FieldType.Keyword)
    String creatorId;

    @Field(type = FieldType.Keyword)
    String ownerId;

    @Field(type = FieldType.Keyword)
    String ragPlanId;

    // ── Diagnosis ─────────────────────────────────────────────────────────────

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "fullname_index_analyzer",
                    searchAnalyzer = "fullname_search_analyzer"
            ),
            otherFields = {
                    @InnerField(
                            suffix = "fuzzy",
                            type = FieldType.Text,
                            analyzer = "fullname_fuzzy_analyzer",
                            searchAnalyzer = "fullname_fuzzy_analyzer"
                    ),
                    @InnerField(
                            suffix = "keyword",
                            type = FieldType.Keyword,
                            normalizer = "lowercase_normalizer"
                    )
            }
    )
    String planName;

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "fullname_index_analyzer",
                    searchAnalyzer = "fullname_search_analyzer"
            ),
            otherFields = {
                    @InnerField(
                            suffix = "fuzzy",
                            type = FieldType.Text,
                            analyzer = "fullname_fuzzy_analyzer",
                            searchAnalyzer = "fullname_fuzzy_analyzer"
                    ),
                    @InnerField(
                            suffix = "keyword",
                            type = FieldType.Keyword,
                            normalizer = "lowercase_normalizer"
                    )
            }
    )
    String diseaseName;

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "fullname_index_analyzer",
                    searchAnalyzer = "fullname_search_analyzer"
            ),
            otherFields = {
                    @InnerField(
                            suffix = "fuzzy",
                            type = FieldType.Text,
                            analyzer = "fullname_fuzzy_analyzer",
                            searchAnalyzer = "fullname_fuzzy_analyzer"
                    ),
                    @InnerField(
                            suffix = "keyword",
                            type = FieldType.Keyword,
                            normalizer = "lowercase_normalizer"
                    )
            }
    )
    String question;

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "fullname_index_analyzer",
                    searchAnalyzer = "fullname_search_analyzer"
            ),
            otherFields = {
                    @InnerField(
                            suffix = "fuzzy",
                            type = FieldType.Text,
                            analyzer = "fullname_fuzzy_analyzer",
                            searchAnalyzer = "fullname_fuzzy_analyzer"
                    ),
                    @InnerField(
                            suffix = "keyword",
                            type = FieldType.Keyword,
                            normalizer = "lowercase_normalizer"
                    )
            }
    )
    String source;

    @Field(type = FieldType.Double)
    Double confidenceScore;

    @Field(type = FieldType.Keyword)
    String severityLevel;

    @Field(type = FieldType.Keyword)
    String urgency;

    // ── Plan metadata ─────────────────────────────────────────────────────────

    @Field(type = FieldType.Keyword)
    List<String> requiredInputs;

    @Field(type = FieldType.Keyword)
    List<String> safetyWarnings;

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "fullname_index_analyzer",
                    searchAnalyzer = "fullname_search_analyzer"
            ),
            otherFields = {
                    @InnerField(
                            suffix = "fuzzy",
                            type = FieldType.Text,
                            analyzer = "fullname_fuzzy_analyzer",
                            searchAnalyzer = "fullname_fuzzy_analyzer"
                    ),
                    @InnerField(
                            suffix = "keyword",
                            type = FieldType.Keyword,
                            normalizer = "lowercase_normalizer"
                    )
            }
    )
    String successIndicators;

    @Field(type = FieldType.Text)
    String estimatedCost;

    // ── Visibility ────────────────────────────────────────────────────────────

    @Field(type = FieldType.Boolean)
    Boolean isPublic;

    @Field(type = FieldType.Keyword)
    String sourceType;

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Field(type = FieldType.Integer)
    Integer eventCount;

    @Field(type = FieldType.Long)
    Long applyCount;

    // ── Denormalized creator info ─────────────────────────────────────────────

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "fullname_index_analyzer",
                    searchAnalyzer = "fullname_search_analyzer"
            ),
            otherFields = {
                    @InnerField(
                            suffix = "fuzzy",
                            type = FieldType.Text,
                            analyzer = "fullname_fuzzy_analyzer",
                            searchAnalyzer = "fullname_fuzzy_analyzer"
                    ),
                    @InnerField(
                            suffix = "keyword",
                            type = FieldType.Keyword,
                            normalizer = "lowercase_normalizer"
                    )
            }
    )
    String creatorName;

    @Field(type = FieldType.Keyword, index = false)
    String creatorAvatar;

    @Field(type = FieldType.Keyword)
    String creatorRole;

    @Field(type = FieldType.Boolean)
    Boolean creatorVerified;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    LocalDateTime createdAt;
}
