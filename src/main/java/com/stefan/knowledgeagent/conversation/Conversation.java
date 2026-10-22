package com.stefan.knowledgeagent.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_conversations")
public class Conversation {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConversationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private long version;

    protected Conversation() {
        // Required by JPA.
    }

    private Conversation(UUID id) {
        this.id = id;
        this.status = ConversationStatus.ACTIVE;
    }

    public static Conversation start(UUID id) {
        return new Conversation(id);
    }

    @PrePersist
    void onCreate() {

        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void touch() {

        ensureActive();

        updatedAt = Instant.now();
    }

    public void close() {

        if (status == ConversationStatus.CLOSED) {
            return;
        }

        status = ConversationStatus.CLOSED;
        closedAt = Instant.now();
    }

    public void ensureActive() {

        if (status != ConversationStatus.ACTIVE) {
            throw new IllegalStateException("Conversation is not active");
        }
    }

    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public long getVersion() {
        return version;
    }

}
