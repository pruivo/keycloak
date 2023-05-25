package org.keycloak.models.cache.infinispan;

import org.infinispan.Cache;
import org.jboss.logging.Logger;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.models.cache.infinispan.events.InvalidationEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.cache.infinispan.entities.Revisioned;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 *
 * Some notes on how this works:

 * This implementation manages optimistic locking and version checks itself.  The reason is Infinispan just does behave
 * the way we need it to.  Not saying Infinispan is bad, just that we have specific caching requirements!
 *
 * This is an invalidation cache implementation and requires to caches:
 * Cache 1 is an Invalidation Cache
 * Cache 2 is a local-only revision number cache.
 *
 *
 * Each node in the cluster maintains its own revision number cache for each entry in the main invalidation cache.  This revision
 * cache holds the version counter for each cached entity.
 *
 * Cache listeners do not receive a @CacheEntryInvalidated event if that node does not have an entry for that item.  So, consider the following.

 1. Node 1 gets current counter for user.  There currently isn't one as this user isn't cached.
 2. Node 1 reads user from DB
 3. Node 2 updates user
 4. Node 2 calls cache.remove(user).  This does not result in an invalidation listener event to node 1!
 5. node 1 checks version counter, checks pass. Stale entry is cached.

 The issue is that Node 1 doesn't have an entry for the user, so it never receives an invalidation listener event from Node 2 thus it can't bump the version.  So, when node 1 goes to cache the user it is stale as the version number was never bumped.

 So how is this issue fixed?  here is pseudo code:

 1. Node 1 calls cacheManager.getCurrentRevision() to get the current local version counter of that User
 2. Node 1 getCurrentRevision() pulls current counter for that user
 3. Node 1 getCurrentRevision() adds a "invalidation.key.userid" to invalidation cache.  Its just a marker. nothing else
 4. Node 2 update user
 5. Node 2 does a cache.remove(user) cache.remove(invalidation.key.userid)
 6. Node 1 receives invalidation event for invalidation.key.userid. Bumps the version counter for that user
 7. node 1 version check fails, it doesn't cache the user
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public abstract class CacheManager {

    private final Cache<String, Wrapper> cache;
    private final UpdateCounter counter = new UpdateCounter();

    public CacheManager(Cache<String, Wrapper> cache) {
        this.cache = cache;
    }

    protected abstract Logger getLogger();

    public boolean containsId(String id) {
        Wrapper wrapper = cache.get(id);
        return wrapper != null && wrapper.getObject() != null;
    }

    public long getCurrentCounter() {
        return counter.current();
    }

    public long getCurrentRevision(String id) {
        Wrapper wrapper = cache.get(id);
        return wrapper == null ? counter.current() : wrapper.getCurrentRevision();
    }

    public <T extends Revisioned> T get(String id, Class<T> type) {
        Wrapper wrapper = cache.get(id);
        if (wrapper == null) {
            if (getLogger().isTraceEnabled()) {
                getLogger().tracev("get() missing rev {0}", id);
            }
            return null;
        }
        Revisioned o = wrapper.getObject();
        return type.isInstance(o) ? type.cast(o) : null;
    }

    public void invalidateObject(String id) {
        cache.compute(id, this::invalidateFunction);
    }

    public void addRevisioned(Revisioned object, long startupRevision) {
        addRevisioned(object, startupRevision, -1);
    }

    public void addRevisioned(Revisioned object, long startupRevision, long lifespan) {
        cache.compute(object.getId(),
                (id, wrapper) -> addRevisionedFunction(wrapper, object, startupRevision),
                lifespan, TimeUnit.MILLISECONDS);
    }

    public void clear() {
        cache.clear();
    }

    public void addInvalidations(Predicate<Map.Entry<String, Revisioned>> predicate, Set<String> invalidations) {
        Iterator<Map.Entry<String, Revisioned>> it = getEntryIterator(predicate);
        while (it.hasNext()) {
            invalidations.add(it.next().getKey());
        }
    }

    private Iterator<Map.Entry<String, Revisioned>> getEntryIterator(Predicate<Map.Entry<String, Revisioned>> predicate) {
        return cache
                .entrySet()
                .stream()
                .filter(CacheManager::notNullRevisioned)
                .map(CacheManager::toEntry)
                .filter(predicate).iterator();
    }

    private static Map.Entry<String, Revisioned> toEntry(Map.Entry<String, Wrapper> entry) {
        return new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().getObject());
    }

    private static boolean notNullRevisioned(Map.Entry<String, Wrapper> entry) {
        return Objects.nonNull(entry.getValue().getObject());
    }


    public static void sendInvalidationEvents(KeycloakSession session, Collection<InvalidationEvent> invalidationEvents, String eventKey) {
        ClusterProvider clusterProvider = session.getProvider(ClusterProvider.class);

        // Maybe add InvalidationEvent, which will be collection of all invalidationEvents? That will reduce cluster traffic even more.
        for (InvalidationEvent event : invalidationEvents) {
            clusterProvider.notify(eventKey, event, true, ClusterProvider.DCNotify.ALL_DCS);
        }
    }


    public void invalidationEventReceived(InvalidationEvent event) {
        Set<String> invalidations = new HashSet<>();

        addInvalidationsFromEvent(event, invalidations);

        getLogger().debugf("[%s] Invalidating %d cache items after received event %s", cache.getCacheManager().getAddress(), invalidations.size(), event);

        for (String invalidation : invalidations) {
            invalidateObject(invalidation);
        }
    }

    protected abstract void addInvalidationsFromEvent(InvalidationEvent event, Set<String> invalidations);

    private Wrapper invalidateFunction(String id, Wrapper current) {
        if (getLogger().isTraceEnabled()) {
            getLogger().tracef("Removed key='%s', value='%s' from cache", id, current.getObject());
        }
        return new InvalidatedWrapper(counter.next());
    }

    private Wrapper addRevisionedFunction(Wrapper oldWrapper, Revisioned object, long startupRevision) {
        long rev = oldWrapper == null ? counter.current() : oldWrapper.getCurrentRevision();
        if (rev > startupRevision) { // revision is ahead transaction start. Other transaction updated in the meantime. Don't cache
            if (getLogger().isTraceEnabled()) {
                getLogger().tracev("Skipped cache. Current revision {0}, Transaction start revision {1}", rev, startupRevision);
            }
            return oldWrapper;
        }
        if (rev > object.getRevision()) { // revision is ahead, don't cache
            if (getLogger().isTraceEnabled()) getLogger().tracev("Skipped cache. Object revision {0}, Cache revision {1}", object.getRevision(), rev);
            return oldWrapper;
        }
        // revisions cache has a lower or equal value than the object.revision, so update revision and add it to cache
        return new RevisionedWrapper(object);
    }

    public interface Wrapper {
        long getCurrentRevision();
        default Revisioned getObject() {
            return null;
        }
    }

    private static class InvalidatedWrapper implements Wrapper {
        private final long revision;

        InvalidatedWrapper(long revision) {
            this.revision = revision;
        }

        @Override
        public long getCurrentRevision() {
            return revision;
        }
    }

    private static class RevisionedWrapper implements Wrapper {
        private final Revisioned object;

        RevisionedWrapper(Revisioned object) {
            this.object = object;
        }

        @Override
        public long getCurrentRevision() {
            return object.getRevision();
        }

        @Override
        public Revisioned getObject() {
            return object;
        }
    }
}
