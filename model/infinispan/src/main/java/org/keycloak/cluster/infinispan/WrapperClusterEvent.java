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

package org.keycloak.cluster.infinispan;

import org.infinispan.protostream.WrappedMessage;
import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoTypeId;
import org.keycloak.cluster.ClusterEvent;
import org.keycloak.marshalling.Marshalling;

import java.util.Objects;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ProtoTypeId(Marshalling.WRAPPED_CLUSTER_EVENT)
public class WrapperClusterEvent implements ClusterEvent {

    private String eventKey;
    private String sender;
    private String senderSite;
    private boolean ignoreSender;
    private boolean ignoreSenderSite;
    private ClusterEvent delegateEvent;

    @ProtoField(1)
    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    @ProtoField(2)
    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    @ProtoField(3)
    public String getSenderSite() {
        return senderSite;
    }

    public void setSenderSite(String senderSite) {
        this.senderSite = senderSite;
    }

    @ProtoField(4)
    public boolean isIgnoreSender() {
        return ignoreSender;
    }

    public void setIgnoreSender(boolean ignoreSender) {
        this.ignoreSender = ignoreSender;
    }

    @ProtoField(5)
    public boolean isIgnoreSenderSite() {
        return ignoreSenderSite;
    }

    public void setIgnoreSenderSite(boolean ignoreSenderSite) {
        this.ignoreSenderSite = ignoreSenderSite;
    }

    public ClusterEvent getDelegateEvent() {
        return delegateEvent;
    }

    public void setDelegateEvent(ClusterEvent delegateEvent) {
        this.delegateEvent = delegateEvent;
    }

    @ProtoField(6)
    WrappedMessage getEventPS() {
        return new WrappedMessage(getDelegateEvent());
    }

    void setEventPS(WrappedMessage eventPS) {
        setDelegateEvent((ClusterEvent) eventPS.getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WrapperClusterEvent that = (WrapperClusterEvent) o;
        return ignoreSender == that.ignoreSender && ignoreSenderSite == that.ignoreSenderSite && Objects.equals(eventKey, that.eventKey) && Objects.equals(sender, that.sender) && Objects.equals(senderSite, that.senderSite) && Objects.equals(delegateEvent, that.delegateEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventKey, sender, senderSite, ignoreSender, ignoreSenderSite, delegateEvent);
    }

    @Override
    public String toString() {
        return String.format("WrapperClusterEvent [ eventKey=%s, sender=%s, senderSite=%s, delegateEvent=%s ]", eventKey, sender, senderSite, delegateEvent.toString());
    }
}
