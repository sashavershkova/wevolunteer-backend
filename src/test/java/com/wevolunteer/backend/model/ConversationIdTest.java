package com.wevolunteer.backend.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConversationId")
class ConversationIdTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ORG_ID = "22222222-2222-2222-2222-222222222222";

    @Nested
    @DisplayName("value and parse")
    class ValueAndParse {

        @Test
        @DisplayName("round-trips through the wire form")
        void roundTrips() {
            ConversationId original = new ConversationId(USER_ID, ORG_ID);

            assertThat(ConversationId.parse(original.value())).isEqualTo(original);
        }

        @Test
        @DisplayName("is derived, so the same pair always produces the same id")
        void isDeterministic() {
            assertThat(new ConversationId(USER_ID, ORG_ID).value())
                    .isEqualTo(new ConversationId(USER_ID, ORG_ID).value());
        }

        @Test
        @DisplayName("does not treat the reversed pair as the same conversation")
        void isOrdered() {
            assertThat(new ConversationId(USER_ID, ORG_ID).value())
                    .isNotEqualTo(new ConversationId(ORG_ID, USER_ID).value());
        }

        @Test
        @DisplayName("rejects a value with the wrong number of segments")
        void rejectsMalformed() {
            assertThatThrownBy(() -> ConversationId.parse(USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Malformed");

            assertThatThrownBy(() -> ConversationId.parse(USER_ID + "_" + ORG_ID + "_extra"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Malformed");
        }

        @Test
        @DisplayName("rejects blank input rather than producing a half-empty pair")
        void rejectsBlank() {
            assertThatThrownBy(() -> ConversationId.parse("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("rejects an id containing the separator, which would let one side impersonate the other")
        void rejectsSeparatorInId() {
            assertThatThrownBy(() -> new ConversationId("user_1", ORG_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("userId");

            assertThatThrownBy(() -> new ConversationId(USER_ID, "org_1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("organizationId");
        }

        @Test
        @DisplayName("rejects blank ids")
        void rejectsBlankIds() {
            assertThatThrownBy(() -> new ConversationId("", ORG_ID))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> new ConversationId(USER_ID, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("participant checks")
    class ParticipantChecks {

        private final ConversationId conversationId = new ConversationId(USER_ID, ORG_ID);

        @Test
        @DisplayName("recognises both participants")
        void recognisesParticipants() {
            assertThat(conversationId.includes(USER_ID)).isTrue();
            assertThat(conversationId.includes(ORG_ID)).isTrue();
        }

        @Test
        @DisplayName("rejects an unrelated subject")
        void rejectsOutsider() {
            assertThat(conversationId.includes("someone-else")).isFalse();
            assertThat(conversationId.participantType("someone-else")).isNull();
        }

        @Test
        @DisplayName("reports which side each participant is on")
        void reportsSide() {
            assertThat(conversationId.participantType(USER_ID)).isEqualTo(ParticipantType.USER);
            assertThat(conversationId.participantType(ORG_ID))
                    .isEqualTo(ParticipantType.ORGANIZATION);
        }
    }
}
