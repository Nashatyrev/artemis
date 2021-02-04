/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.containers.state.genesis;

import java.util.function.Consumer;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.containers.state.DelegatingMutableBeaconState;
import tech.pegasys.teku.spec.containers.state.MutableBeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;

public class DelegatingMutableBeaconStateGenesis extends DelegatingMutableBeaconState
    implements MutableBeaconStateGenesis {
  public DelegatingMutableBeaconStateGenesis(final MutableBeaconState state) {
    super(state);
  }

  @Override
  public void updatePrevious_epoch_attestations(
      final Consumer<SSZMutableList<PendingAttestation>> updater) {
    state.maybeUpdatePrevious_epoch_attestations(
        maybeValue -> updater.accept(maybeValue.orElseThrow()));
  }

  @Override
  public void updateCurrent_epoch_attestations(
      final Consumer<SSZMutableList<PendingAttestation>> updater) {
    state.maybeUpdateCurrent_epoch_attestations(
        maybeValue -> updater.accept(maybeValue.orElseThrow()));
  }
}
