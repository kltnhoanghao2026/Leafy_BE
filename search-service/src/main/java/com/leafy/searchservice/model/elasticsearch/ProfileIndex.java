package com.leafy.searchservice.model.elasticsearch;

import com.leafy.common.enums.ProfileRole;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Setting(settingPath = "elasticsearch/es-setting.json")
@Document(indexName = "profiles")
public class ProfileIndex {

    @Id
    String id;

    @Field(type = FieldType.Keyword)
    String userId;

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
    String fullName;

        @Field(type = FieldType.Keyword)
        String profilePicture;

        @Field(type = FieldType.Keyword)
        String avatar;

    @Field(type = FieldType.Keyword)
    String phoneNumber;

    @Field(type = FieldType.Keyword)
    String email;

    @Field(type = FieldType.Keyword)
    ProfileRole role;

    @Field(type = FieldType.Text)
    String specialty;

    @Field(type = FieldType.Boolean)
    Boolean isVerified;

    @Field(type = FieldType.Boolean)
    Boolean active;

    @Field(type = FieldType.Text)
    String bio;

    @Field(type = FieldType.Text)
    String addressLine;

    @Field(type = FieldType.Keyword)
    String provinceCode;

    @Field(type = FieldType.Keyword)
    String districtCode;

    @Field(type = FieldType.Keyword)
    String wardCode;

    @Field(type = FieldType.Double)
    Double latitude;

    @Field(type = FieldType.Double)
    Double longitude;
}
