package org.infinispan.functional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.infinispan.Cache;
import org.infinispan.commons.api.functional.EntryView.ReadEntryView;
import org.infinispan.commons.api.functional.EntryView.WriteEntryView;
import org.infinispan.persistence.dummy.DummyInMemoryStore;
import org.infinispan.persistence.manager.PersistenceManager;
import org.testng.annotations.Test;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt; && Krzysztof Sobolewski &lt;Krzysztof.Sobolewski@atende.pl&gt;
 */
@Test(groups = "functional", testName = "functional.FunctionalCachestoreTest")
public class FunctionalCachestoreTest extends AbstractFunctionalOpTest {
   @Override
   public Object[] factory() {
      return new Object[] {
         new FunctionalCachestoreTest().persistence(true).passivation(false),
         new FunctionalCachestoreTest().persistence(true).passivation(true)
      };
   }

   @Override
   protected String parameters() {
      return "[passivation=" + passivation + "]";
   }

   @Test(dataProvider = "owningModeAndWriteMethod")
   public void testWriteLoad(boolean isSourceOwner, WriteMethod method) throws InterruptedException {
      Object key = getKey(isSourceOwner);

      List<Cache<Object, Object>> owners = caches(DIST).stream()
            .filter(cache -> cache.getAdvancedCache().getDistributionManager().getLocality(key).isLocal())
            .collect(Collectors.toList());

      method.action.eval(key, wo, rw,
            (Function<ReadEntryView<Object, String>, Void> & Serializable) view -> { assertFalse(view.find().isPresent()); return null; },
            (BiConsumer<WriteEntryView<String>, Void> & Serializable) (view, nil) -> view.set("value"), getClass());

      assertInvocations(2);

      caches(DIST).forEach(cache -> assertEquals(cache.get(key), "value", getAddress(cache).toString()));
      // Staggered gets could arrive after evict command and that would reload the entry into DC
      advanceGenerationsAndAwait(10, TimeUnit.SECONDS);
      caches(DIST).forEach(cache -> cache.evict(key));
      caches(DIST).forEach(cache -> assertFalse(cache.getAdvancedCache().getDataContainer().containsKey(key), getAddress(cache).toString()));
      owners.forEach(cache -> {
         DummyInMemoryStore store = getStore(cache);
         assertTrue(store.contains(key), getAddress(cache).toString());
      });

      method.action.eval(key, wo, rw,
            (Function<ReadEntryView<Object, String>, Void> & Serializable) view -> {
               assertTrue(view.find().isPresent());
               assertEquals(view.get(), "value");
               return null;
            },
            (BiConsumer<WriteEntryView<String>, Void> & Serializable) (view, nil) -> {}, getClass());

      assertInvocations(4);
   }

   public DummyInMemoryStore getStore(Cache cache) {
      Set<DummyInMemoryStore> stores = cache.getAdvancedCache().getComponentRegistry().getComponent(PersistenceManager.class).getStores(DummyInMemoryStore.class);
      return stores.iterator().next();
   }

   @Test(dataProvider = "writeMethods")
   public void testWriteLoadLocal(WriteMethod method) {
      Integer key = 1;

      method.action.eval(key, lwo, lrw,
         (Function<ReadEntryView<Integer, String>, Void> & Serializable) view -> { assertFalse(view.find().isPresent()); return null; },
         (BiConsumer<WriteEntryView<String>, Void> & Serializable) (view, nil) -> view.set("value"), getClass());

      assertInvocations(1);

      Cache<Integer, String> cache = cacheManagers.get(0).getCache();
      assertEquals(cache.get(key), "value");
      cache.evict(key);
      assertFalse(cache.getAdvancedCache().getDataContainer().containsKey(key));

      DummyInMemoryStore store = getStore(cache);
      assertTrue(store.contains(key));

      method.action.eval(key, lwo, lrw,
         (Function<ReadEntryView<Object, String>, Void> & Serializable) view -> {
            assertTrue(view.find().isPresent());
            assertEquals(view.get(), "value");
            return null;
         },
         (BiConsumer<WriteEntryView<String>, Void> & Serializable) (view, nil) -> {}, getClass());

      assertInvocations(2);
   }

   @Test(dataProvider = "owningModeAndReadMethod")
   public void testReadLoad(boolean isSourceOwner, ReadMethod method) {
      Object key = getKey(isSourceOwner);
      List<Cache<Object, Object>> owners = caches(DIST).stream()
            .filter(cache -> cache.getAdvancedCache().getDistributionManager().getLocality(key).isLocal())
            .collect(Collectors.toList());

      assertTrue((Boolean) method.action.eval(key, ro,
            (Function<ReadEntryView<Object, String>, Boolean> & Serializable) view -> { assertFalse(view.find().isPresent()); return true; }));

      // we can't add from read-only cache, so we put manually:
      cache(0, DIST).put(key, "value");

      caches(DIST).forEach(cache -> assertEquals(cache.get(key), "value", getAddress(cache).toString()));
      caches(DIST).forEach(cache -> cache.evict(key));
      caches(DIST).forEach(cache -> assertFalse(cache.getAdvancedCache().getDataContainer().containsKey(key), getAddress(cache).toString()));
      owners.forEach(cache -> {
         Set<DummyInMemoryStore> stores = cache.getAdvancedCache().getComponentRegistry().getComponent(PersistenceManager.class).getStores(DummyInMemoryStore.class);
         DummyInMemoryStore store = stores.iterator().next();
         assertTrue(store.contains(key), getAddress(cache).toString());
      });

      assertEquals(method.action.eval(key, ro,
            (Function<ReadEntryView<Object, String>, Object> & Serializable) view -> {
               assertTrue(view.find().isPresent());
               assertEquals(view.get(), "value");
               return "OK";
            }), "OK");
   }
}