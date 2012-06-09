package org.infinispan.stats;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.stats.translations.ExposedStatistics.IspnStats;
import org.infinispan.stats.translations.LocalStatistics;
import org.infinispan.transaction.LockingMode;

/**
 * Websiste: www.cloudtm.eu
 * Date: 20/04/12
 *
 * @author Diego Didona <didona@gsd.inesc-id.pt>
 * @author Pedro Ruivo
 * @since 5.2
 */
public class LocalTransactionStatistics extends TransactionStatistics {

   private boolean stillLocalExecution;

   public LocalTransactionStatistics(Configuration configuration) {
      super(LocalStatistics.getSize(), configuration);
      this.stillLocalExecution = true;
   }

   public final void terminateLocalExecution() {
      this.stillLocalExecution = false;

      if (!isReadOnly()) {
         this.addValue(IspnStats.WR_TX_LOCAL_EXECUTION_TIME, System.nanoTime() - this.initTime);
      }
      this.incrementValue(IspnStats.NUM_PREPARES);
   }

   public final boolean isStillLocalExecution() {
      return this.stillLocalExecution;
   }

   @Override
   protected final void terminate() {
      if (!isReadOnly() && isCommit()) {
         long numPuts = this.getValue(IspnStats.NUM_PUTS);
         this.addValue(IspnStats.NUM_SUCCESSFUL_PUTS, numPuts);
         this.addValue(IspnStats.NUM_HELD_LOCKS_SUCCESS_TX, getValue(IspnStats.NUM_HELD_LOCKS));
         if (isCommit()) {
            if (configuration.transaction().lockingMode() == LockingMode.OPTIMISTIC) {
               this.addValue(IspnStats.LOCAL_EXEC_NO_CONT, this.getValue(IspnStats.WR_TX_LOCAL_EXECUTION_TIME));
            } else {
               long localLockAcquisitionTime = getValue(IspnStats.LOCK_WAITING_TIME);
               long totalLocalDuration = this.getValue(IspnStats.WR_TX_LOCAL_EXECUTION_TIME);
               this.addValue(IspnStats.LOCAL_EXEC_NO_CONT, (totalLocalDuration - localLockAcquisitionTime));
            }
         }
      }
   }

   protected final void onPrepareCommand() {
      this.terminateLocalExecution();
   }

   protected final int getIndex(IspnStats stat) throws NoIspnStatException {
      int ret = LocalStatistics.getIndex(stat);
      if (ret == LocalStatistics.NOT_FOUND) {
         throw new NoIspnStatException("IspnStats " + stat + " not found!");
      }
      return ret;
   }

   @Override
   public final String toString() {
      return "LocalTransactionStatistics{" +
            "stillLocalExecution=" + stillLocalExecution +
            ", " + super.toString();
   }
}
