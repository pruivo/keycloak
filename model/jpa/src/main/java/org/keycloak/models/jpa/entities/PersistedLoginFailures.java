/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.models.jpa.entities;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@IdClass(PersistedLoginFailures.Key.class)
@Table(name="LOGIN_FAILURES")
@NamedQueries({
        @NamedQuery(
                name = "loginFailureDelete",
                query = "DELETE FROM PersistedLoginFailures e" +
                        " WHERE e.realmId = :realmId AND e.userId = :userId"),
        @NamedQuery(
                name = "loginFailureDeleteMulti",
                query = "DELETE FROM PersistedLoginFailures e" +
                        " WHERE e.segment = :segment AND e.realmId = :realmId AND e.userId IN (:userId)"),
        @NamedQuery(
                name = "loginFailureDeleteRealm",
                query = "DELETE FROM PersistedLoginFailures e" +
                        " WHERE e.realmId = :realmId"),
        @NamedQuery(
                name = "loginFailureSegmentSize",
                query = "SELECT COUNT(*) FROM PersistedLoginFailures e WHERE e.segment IN :segments"),
        @NamedQuery(
                name = "loginFailureClear",
                query = "DELETE FROM PersistedLoginFailures e"),
        @NamedQuery(
                name = "loginFailurePublishKeys",
                query = "SELECT e.realmId, e.userId" +
                        " FROM PersistedLoginFailures e" +
                        " WHERE e.segment = :segment" +
                        " ORDER BY e.realmId, e.userId"),
        @NamedQuery(
                name = "loginFailurePublishEntries",
                query = "SELECT e" +
                        " FROM PersistedLoginFailures e" +
                        " WHERE e.segment = :segment" +
                        " ORDER BY e.realmId, e.userId"),
        @NamedQuery(
                name = "loginFailureExpiration",
                query = "SELECT e" +
                        " FROM PersistedLoginFailures e" +
                        " WHERE e.segment = :segment AND e.realmId = :realmId AND e.createdOn < :createdOn" +
                        " ORDER BY e.realmId, e.userId"),
})
public class PersistedLoginFailures {

    // primary key
    @Id
    @Column(name = "REALM_ID", length = 36, nullable = false)
    private String realmId;
    @Id
    @Column(name = "USER_ID", nullable = false)
    private String userId;

    // infinispan metadata
    @Column(name = "SEGMENT")
    private int segment;
    @Column(name = "CREATED_ON")
    private long createdOn;

    // infinispan data
    @Column(name = "DATA", nullable = false)
    @Lob
    private byte[] data;

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public int getSegment() {
        return segment;
    }

    public void setSegment(int segment) {
        this.segment = segment;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(long createOn) {
        this.createdOn = createOn;
    }

    @Override
    public String toString() {
        return "PersistedLoginFailures{" +
                "createOn=" + createdOn +
                ", realmId='" + realmId + '\'' +
                ", segment=" + segment +
                ", userId='" + userId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        PersistedLoginFailures that = (PersistedLoginFailures) o;
        return segment == that.segment && createdOn == that.createdOn && realmId.equals(that.realmId) && userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        int result = realmId.hashCode();
        result = 31 * result + userId.hashCode();
        result = 31 * result + segment;
        result = 31 * result + Long.hashCode(createdOn);
        return result;
    }

    public record Key(String realmId, String userId) implements Serializable {
    }
}
