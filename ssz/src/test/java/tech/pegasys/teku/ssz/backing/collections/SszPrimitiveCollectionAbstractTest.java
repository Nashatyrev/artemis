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

package tech.pegasys.teku.ssz.backing.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.SszCollectionAbstractTest;
import tech.pegasys.teku.ssz.backing.SszPrimitive;

public interface SszPrimitiveCollectionAbstractTest extends SszCollectionAbstractTest {

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT, SszT>> void getElement_shouldReturnUnboxedElement(
      SszPrimitiveCollection<ElT, SszT> collection) {
    for (int i = 0; i < collection.size(); i++) {
      assertThat(collection.getElement(i)).isEqualTo(collection.get(i).get());
    }
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void getElement_shouldThrowIndexOfBounds(SszPrimitiveCollection<?, ?> collection) {
    assertThatThrownBy(() -> collection.getElement(-1))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> collection.getElement(collection.size()))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(
            () ->
                collection.getElement(
                    (int) Long.min(Integer.MAX_VALUE, collection.getSchema().getMaxLength())))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }
}
