package org.infinispan.client.hotrod.query;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.Index;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.protostream.sampledomain.marshallers.MarshallerRegistration;
import org.infinispan.query.remote.ProtobufMetadataManager;
import org.testng.annotations.Test;

import static org.infinispan.server.hotrod.test.HotRodTestingUtil.hotRodCacheConfiguration;

/**
 * Verifies the functionality of the Queries in case of REPL infinispan directory_provider for clustered Hotrod servers.
 *
 * @author Anna Manukyan
 */
@Test(testName = "client.hotrod.query.MultiHotRodServerIspnDirReplQueryTest", groups = "unstable", description = "Enable tests when ISPN-3672 is fixed. -- original group: functional")
public class MultiHotRodServerIspnDirReplQueryTest extends MultiHotRodServerQueryTest {

   @Override
   protected void createCacheManagers() throws Throwable {
      ConfigurationBuilder defaultConfiguration = getDefaultClusteredCacheConfig(CacheMode.REPL_SYNC, false);
      ConfigurationBuilder builder = hotRodCacheConfiguration(getDefaultClusteredCacheConfig(CacheMode.REPL_SYNC, false));
      builder.indexing().index(Index.LOCAL)
            .addProperty("default.directory_provider", "infinispan")
            .addProperty("default.exclusive_index_use", "false")
            //.addProperty("default.indexmanager", "org.infinispan.query.indexmanager.InfinispanIndexManager")
            .addProperty("lucene_version", "LUCENE_CURRENT");

      createHotRodServers(2, defaultConfiguration);

      //initialize server-side serialization context
      for (EmbeddedCacheManager cm : cacheManagers) {
         cm.defineConfiguration("queryableCache", builder.build());
         cm.getGlobalComponentRegistry().getComponent(ProtobufMetadataManager.class)
                 .registerProtofiles("/sample_bank_account/bank.proto", "/infinispan/indexing.proto", "/google/protobuf/descriptor.proto");
      }

      //initialize client-side serialization context
      for (RemoteCacheManager rcm : clients) {
         MarshallerRegistration.registerMarshallers(ProtoStreamMarshaller.getSerializationContext(rcm));
      }

      waitForClusterToForm();

      remoteCache0 = client(0).getCache("queryableCache");
      remoteCache1 = client(1).getCache("queryableCache");
   }

   @Test(groups = "unstable")
   public void testAttributeQuery() throws Exception {
      super.testAttributeQuery();
   }

   @Test(groups = "unstable")
   public void testEmbeddedAttributeQuery() throws Exception {
      super.testEmbeddedAttributeQuery();
   }

   @Test(groups = "unstable")
   public void testProjections() throws Exception {
      super.testProjections();
   }
}
