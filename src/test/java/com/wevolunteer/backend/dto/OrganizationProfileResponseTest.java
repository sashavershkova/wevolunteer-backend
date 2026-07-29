package com.wevolunteer.backend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wevolunteer.backend.model.Organization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrganizationProfileResponse")
class OrganizationProfileResponseTest {

    private static final String ORG_ID = "org-1";
    private static final String IMAGE_KEY = "organizations/org-1/profile/abc.png";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("from")
    class From {

        @Test
        @DisplayName("copies every public profile field")
        void copiesPublicFields() {
            Organization organization = new Organization(
                    ORG_ID, "Green Earth", "We clean beaches",
                    "info@greenearth.org", "https://greenearth.org");

            OrganizationProfileResponse response =
                    OrganizationProfileResponse.from(organization);

            assertThat(response.organizationId()).isEqualTo(ORG_ID);
            assertThat(response.name()).isEqualTo("Green Earth");
            assertThat(response.description()).isEqualTo("We clean beaches");
            assertThat(response.email()).isEqualTo("info@greenearth.org");
            assertThat(response.website()).isEqualTo("https://greenearth.org");
        }

        @Test
        @DisplayName("maps a profile with no image and no optional fields")
        void mapsSparseProfile() {
            Organization organization = new Organization(
                    ORG_ID, "Green Earth", null, "info@greenearth.org", null);

            OrganizationProfileResponse response =
                    OrganizationProfileResponse.from(organization);

            assertThat(organization.profileImageKey()).isNull();
            assertThat(response.description()).isNull();
            assertThat(response.website()).isNull();
        }
    }

    @Nested
    @DisplayName("serialization")
    class Serialization {

        @Test
        @DisplayName("never exposes the S3 object key to API clients")
        void doesNotExposeObjectKey() throws Exception {
            Organization organization = new Organization(
                    ORG_ID, "Green Earth", "We clean beaches",
                    "info@greenearth.org", "https://greenearth.org", IMAGE_KEY);

            String json = objectMapper.writeValueAsString(
                    OrganizationProfileResponse.from(organization));

            assertThat(json).doesNotContain("profileImageKey");
            assertThat(json).doesNotContain(IMAGE_KEY);
            assertThat(json).doesNotContain("organizations/");
        }
    }
}
