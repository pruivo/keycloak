/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.cache.infinispan.events;

import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoTypeId;
import org.keycloak.models.GroupModel;
import org.keycloak.models.cache.infinispan.RealmCacheManager;
import org.keycloak.marshalling.Marshalling;

import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ProtoTypeId(Marshalling.GROUP_REMOVED_EVENT)
public class GroupRemovedEvent extends InvalidationEvent implements RealmCacheInvalidationEvent {

    private String groupId;
    private String parentId;
    private String realmId;

    public static GroupRemovedEvent create(GroupModel group, String realmId) {
        GroupRemovedEvent event = new GroupRemovedEvent();
        event.realmId = realmId;
        event.groupId = group.getId();
        event.parentId = group.getParentId();
        return event;
    }

    @Override
    public String getId() {
        return groupId;
    }

    void setId(String id) {
        this.groupId = id;
    }

    @ProtoField(2)
    String getRealmId() {
        return realmId;
    }

    void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    @ProtoField(3)
    String getParentId() {
        return parentId;
    }

    void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @Override
    public String toString() {
        return String.format("GroupRemovedEvent [ realmId=%s, groupId=%s, parentId=%s ]", realmId, groupId, parentId);
    }

    @Override
    public void addInvalidations(RealmCacheManager realmCache, Set<String> invalidations) {
        realmCache.groupQueriesInvalidations(realmId, invalidations);
        realmCache.groupNameInvalidations(groupId, invalidations);
        if (parentId != null) {
            invalidations.add(parentId);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        GroupRemovedEvent that = (GroupRemovedEvent) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(parentId, that.parentId) && Objects.equals(realmId, that.realmId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupId, parentId, realmId);
    }

}
