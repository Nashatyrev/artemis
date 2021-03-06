/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.attestation;

import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoiceAttestationProcessor {

  private final RecentChainData recentChainData;
  private final ForkChoice forkChoice;

  public ForkChoiceAttestationProcessor(
      final RecentChainData recentChainData, final ForkChoice forkChoice) {
    this.recentChainData = recentChainData;
    this.forkChoice = forkChoice;
  }

  public SafeFuture<AttestationProcessingResult> processAttestation(
      final ValidateableAttestation attestation) {
    return forkChoice.onAttestation(attestation);
  }

  public void applyIndexedAttestationToForkChoice(final IndexedAttestation attestation) {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    forkChoice.applyIndexedAttestation(transaction, attestation);
    transaction.commit(() -> {}, "Failed to persist attestation result");
  }
}
