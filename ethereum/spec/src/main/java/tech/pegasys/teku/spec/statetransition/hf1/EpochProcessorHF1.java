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

package tech.pegasys.teku.spec.statetransition.hf1;

import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.hf1.BeaconStateHF1;
import tech.pegasys.teku.spec.datastructures.state.hf1.MutableBeaconStateHF1;
import tech.pegasys.teku.spec.statetransition.EpochProcessor;
import tech.pegasys.teku.spec.statetransition.genesis.EpochProcessorGenesis;

public class EpochProcessorHF1 extends EpochProcessorGenesis implements EpochProcessor {

  @Override
  public BeaconState<?> processEpoch(final BeaconState<?> preState) {
    final BeaconStateHF1 versionedPreState =
        preState
            .toHF1Version()
            .orElseThrow(() -> new IllegalArgumentException("Unexpected BeaconState version"));
    return processEpoch(versionedPreState);
  }

  protected BeaconStateHF1 processEpoch(final BeaconStateHF1 preState) {
    return preState.updatedHF1(
        state -> {
          // TODO other methods etc
          processRewardsAndPenalties(state);
          processFinalUpdates(state);
        });
  }

  protected void processRewardsAndPenalties(final MutableBeaconStateHF1 beaconState) {
    // TODO
  }
}
