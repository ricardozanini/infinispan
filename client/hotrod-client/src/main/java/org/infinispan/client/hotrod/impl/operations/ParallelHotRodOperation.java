package org.infinispan.client.hotrod.impl.operations;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.impl.protocol.Codec;
import org.infinispan.client.hotrod.impl.transport.netty.ChannelFactory;

import io.netty.buffer.ByteBuf;

/**
 * An HotRod operation that span across multiple remote nodes concurrently (like getAll / putAll).
 *
 * @author Guillaume Darmont / guillaume@dropinocean.com
 */
public abstract class ParallelHotRodOperation<T, SUBOP extends HotRodOperation<T>> extends HotRodOperation<T> {
   protected final ChannelFactory channelFactory;

   protected ParallelHotRodOperation(Codec codec, ChannelFactory channelFactory, byte[] cacheName, AtomicInteger
         topologyId, int flags, Configuration cfg) {
      super(codec, flags, cfg, cacheName, topologyId, channelFactory);
      this.channelFactory = channelFactory;
   }

   @Override
   public CompletableFuture<T> execute() {
      List<SUBOP> operations = mapOperations();

      if (operations.isEmpty()) {
         return CompletableFuture.completedFuture(createCollector());
      } else if (operations.size() == 1) {
         // Only one operation to do, we stay in the caller thread
         return operations.get(0).execute();
      } else {
         // Multiple operation, submit to the thread poll
         return executeParallel(operations);
      }
   }

   private CompletableFuture<T> executeParallel(List<SUBOP> operations) {
      T collector = createCollector();
      AtomicInteger counter = new AtomicInteger(operations.size());
      for (SUBOP operation : operations) {
         operation.execute().whenComplete((result, throwable) -> {
            if (throwable != null) {
               completeExceptionally(throwable);
            } else {
               if (collector != null) {
                  synchronized (collector) {
                     combine(collector, result);
                  }
               }
               if (counter.decrementAndGet() == 0) {
                  complete(collector);
               }
            }
         });
      }
      this.exceptionally(throwable -> {
         for (SUBOP operation : operations) {
            operation.cancel(true);
         }
         return null;
      });
      return this;
   }

   protected abstract List<SUBOP> mapOperations();

   protected abstract T createCollector();

   protected abstract void combine(T collector, T result);

   @Override
   public T decodePayload(ByteBuf buf, short status) {
      throw new UnsupportedOperationException();
   }
}
