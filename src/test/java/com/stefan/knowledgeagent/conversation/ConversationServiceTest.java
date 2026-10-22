package com.stefan.knowledgeagent.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository repository;

    @Mock
    private ChatMemory chatMemory;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(repository, chatMemory);
    }

    @Test
    void createsAnActiveConversationWhenIdIsNotProvided() {

        when(repository.save(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID conversationId = service.getOrCreateActiveId(null);

        ArgumentCaptor<Conversation> captor =
                ArgumentCaptor.forClass(Conversation.class);

        verify(repository).save(captor.capture());

        Conversation saved = captor.getValue();

        assertThat(conversationId).isNotNull();
        assertThat(saved.getId()).isEqualTo(conversationId);
        assertThat(saved.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void reusesAnExistingActiveConversation() {

        UUID conversationId = UUID.randomUUID();
        Conversation conversation = Conversation.start(conversationId);

        when(repository.findById(conversationId))
                .thenReturn(Optional.of(conversation));

        UUID result = service.getOrCreateActiveId(conversationId);

        assertThat(result).isEqualTo(conversationId);
        assertThat(conversation.isActive()).isTrue();
    }

    @Test
    void rejectsAnUnknownConversation() {

        UUID conversationId = UUID.randomUUID();

        when(repository.findById(conversationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.getOrCreateActiveId(conversationId)
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode())
                                    .isEqualTo(HttpStatus.NOT_FOUND);
                            assertThat(exception.getReason())
                                    .isEqualTo("Conversation not found");
                        }
                );
    }

    @Test
    void rejectsAClosedConversation() {

        UUID conversationId = UUID.randomUUID();
        Conversation conversation = Conversation.start(conversationId);

        conversation.close();

        when(repository.findById(conversationId))
                .thenReturn(Optional.of(conversation));

        assertThatThrownBy(
                () -> service.getOrCreateActiveId(conversationId)
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode())
                                    .isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.getReason())
                                    .isEqualTo("Conversation is closed");
                        }
                );
    }

    @Test
    void closesConversationAndClearsItsChatMemory() {

        UUID conversationId = UUID.randomUUID();
        Conversation conversation = Conversation.start(conversationId);

        when(repository.findById(conversationId))
                .thenReturn(Optional.of(conversation));

        service.close(conversationId);

        assertThat(conversation.getStatus())
                .isEqualTo(ConversationStatus.CLOSED);
        assertThat(conversation.getClosedAt()).isNotNull();

        verify(repository).save(conversation);
        verify(chatMemory).clear(conversationId.toString());
    }

    @Test
    void rejectsClosingAnUnknownConversation() {

        UUID conversationId = UUID.randomUUID();

        when(repository.findById(conversationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(conversationId))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND)
                );
    }
}
